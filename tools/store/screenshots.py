"""Takes the Play Store phone screenshots of the debug app on a connected device, for one language.

    python tools/store/screenshots.py en-US        # or fr-FR, de-DE, es-ES, it-IT, ru-RU

- Needs `adb`, one device (Android 13+ for the per-app language) with the app installed and its data
  fresh (`adb shell pm clear thibautperrin.tetrano`), and Pillow.
- The screen is forced to 1080x1920 (Play wants a side ratio of at most 2:1; a tall phone like the
  Pixel 4a's 1080x2340 is refused) and put back afterwards.
- Screenshots land in fastlane/metadata/android/<language>/images/phoneScreenshots/1.png ...
- It drives the app by tapping at coordinates measured on a 1080x1920 screen at 440 dpi, so on other
  devices adjust the constants below. Every step is checked against the screen's text.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

LANG = sys.argv[1] if len(sys.argv) > 1 else "en-US"
PKG = "thibautperrin.tetrano"
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "fastlane/metadata/android" / LANG / "images/phoneScreenshots"
OUT.mkdir(parents=True, exist_ok=True)

# Board geometry on 1080x1920: cell centers and the ring tray (4 sizes, small to large).
COLS = [141, 407, 673, 938]
ROWS = [428, 694, 960, 1226]
TRAY_Y = 1492
SIZES = COLS


def adb(*args, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True, env={**__import__("os").environ, "MSYS_NO_PATHCONV": "1"})
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def tap(x, y, wait=1.5):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def drag(x1, y1, x2, y2):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), "600")
    time.sleep(0.8)


def texts():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    try:
        root = ET.fromstring(xml[xml.index("<?xml"):] if "<?xml" in xml else xml)
    except ET.ParseError:
        return []
    return [n.get("text") or n.get("content-desc") or "" for n in root.iter("node")]


def tap_text(pattern, wait=1.5):
    """Taps the center of the first element whose text matches (dialog buttons move with the language)."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    root = ET.fromstring(xml[xml.index("<?xml"):])
    for n in root.iter("node"):
        if re.search(pattern, n.get("text") or ""):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            return tap((x1 + x2) // 2, (y1 + y2) // 2, wait)
    raise SystemExit(f"no element matching /{pattern}/")


def wait_for(pattern, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if any(re.search(pattern, t) for t in texts()):
            return True
        time.sleep(1.5)
    raise SystemExit(f"timeout waiting for /{pattern}/ — is the app in {LANG}?")


def shot(n):
    time.sleep(0.7)
    (OUT / f"{n}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    print("saved", n)


def back(times=1):
    for _ in range(times):
        adb("shell", "input", "keyevent", "BACK")
        time.sleep(0.9)


# The analysis screen's board is smaller (the analysis panel takes the room below it).
ANALYSIS = ([266, 446, 626, 806], [390, 570, 750, 930], [264, 448, 632, 816], 1120)


def play_ring(size, col, row, geometry=None):
    cols, rows, tray_x, tray_y = geometry or (COLS, ROWS, SIZES, TRAY_Y)
    drag(tray_x[size], tray_y, cols[col], rows[row])


def launch():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(3)


adb("shell", "wm", "size", "1080x1920")
adb("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", LANG.split("-")[0])
adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
try:
    launch()
    # Home buttons: Play, Rules, Settings, About (centers measured on 1080x1920).
    shot(1)                                  # 1 home
    tap(540, 433)
    shot(2)                                  # 2 play menu
    tap(540, 635)                            # 1 player
    tap(540, 1405)                           # Play (level kept at its default)
    time.sleep(2)
    # A few moves: our ring, then wait for the computer's reply.
    for size, col, row in [(3, 1, 1), (1, 2, 2), (2, 1, 2)]:
        play_ring(size, col, row)
        time.sleep(3)
        wait_for(r"turn|tour|dran|turno|tocca|ход", 90)
    shot(3)                                  # 3 game
    back(3)
    launch()
    tap(540, 433)
    tap(540, 1681)                           # Puzzles
    wait_for(r"^(OK|ОК)$")
    tap_text(r"^(OK|ОК)$")                   # dismiss the explanations
    shot(4)                                  # 4 puzzle
    back()
    tap(540, 433)
    tap(540, 1333)                           # Challenge
    wait_for(r"1/2")
    tap_text(r"^(Next|Suivant|Weiter|Siguiente|Avanti|Далее)$")
    tap_text(r"^(Play|Jouer|Spielen|Jugar|Gioca|Играть)$")
    shot(5)                                  # 5 challenge
    back()
    tap(540, 984)                            # Analyse games -> empty board
    tap(400, 430)
    for size, col, row in [(3, 1, 1), (1, 2, 2), (2, 1, 2), (0, 0, 3)]:
        play_ring(size, col, row, ANALYSIS)
    time.sleep(6)                            # let the live analysis run
    shot(6)                                  # 6 analysis
    back(3)
    tap(540, 635)                            # Rules
    shot(7)                                  # 7 rules
    back()
    tap(540, 837)                            # Settings
    shot(8)                                  # 8 settings
finally:
    adb("shell", "wm", "size", "reset")
