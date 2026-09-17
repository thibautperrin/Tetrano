package android.os;

/**
 * Minimal stand-in for {@code android.os.Handler}, just enough for the
 * legacy {@code IA} class (see docs/PLAN.md P3.2) to compile outside
 * Android. The differential test never passes a real {@link Handler}
 * instance (it always passes {@code null}, exactly like most of the
 * legacy unit tests did), so nothing here needs to actually do anything.
 */
public class Handler {
    public Message obtainMessage(int what, int arg1, int arg2) {
        return new Message();
    }
}
