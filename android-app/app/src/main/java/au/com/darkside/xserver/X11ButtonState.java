package au.com.darkside.xserver;

/** X11 button events carry the logical state immediately BEFORE the transition. */
final class X11ButtonState {
    private X11ButtonState() {}
    static int beforeTransition(int after, int button) {
        return after ^ (0x80 << button);
    }
}
