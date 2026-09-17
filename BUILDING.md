# Building Tetrano

## A machine-specific Gradle daemon quirk

On at least one development machine used for this project, the Gradle
daemon fails to start with:

```
java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
    at java.base/sun.nio.ch.UnixDomainSockets.connect0(Native Method)
```

This is a JDK NIO issue (`Selector.open()` tries to create its wakeup pipe
using a Unix Domain Socket, which fails on that machine — most likely
because of the Fortinet network filter driver seen in its interface list).
It is **not** specific to this project: it reproduces with a one-line Java
program that just calls `Selector.open()`, and affects every Gradle project
on that machine (including via Android Studio, which starts its own daemon
the same way).

The fix is the JVM flag `-Djdk.net.unixdomain.tmpdir=INVALID`, which makes
the JDK fall back to a plain TCP loopback pipe instead of a Unix Domain
Socket. It is harmless on machines that don't have this problem.

- `gradlew` / `gradlew.bat` already pass this flag to the one-off wrapper
  launcher JVM (see `DEFAULT_JVM_OPTS`), so a fresh `--no-daemon` build
  works out of the box.
- The **daemon** JVM's arguments come from `org.gradle.jvmargs` in
  `gradle.properties` — but if a personal `gradle.properties` under
  `GRADLE_USER_HOME` (usually `~/.gradle/gradle.properties`) also defines
  `org.gradle.jvmargs`, it takes precedence over this project's value, and
  the flag never reaches the daemon. This repository's `gradle.properties`
  cannot fix that for you (and this project never modifies your personal,
  global Gradle configuration — it may hold your own credentials).

**If you hit this error**, pick one:

1. Add `-Djdk.net.unixdomain.tmpdir=INVALID` to the `org.gradle.jvmargs`
   line in your own `~/.gradle/gradle.properties`, or
2. Set the standard JDK environment variable `JDK_JAVA_OPTIONS` (or
   `JAVA_TOOL_OPTIONS`) to `-Djdk.net.unixdomain.tmpdir=INVALID`, persisted
   in your Windows user environment variables — this fixes every Java
   process, including Android Studio's own daemon, or
3. Export `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=INVALID` for the
   current shell session before running `./gradlew`.

If you don't hit this error at all, ignore this file — nothing here is
required for a normal build.
