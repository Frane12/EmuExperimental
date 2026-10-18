package au.com.darkside.xserver;

/**
 * X11 GetWindowAttributes map_state values (core protocol):
 * 0 = IsUnmapped, 1 = IsUnviewable, 2 = IsViewable.
 *
 * A mapped child of an unmapped parent is NOT viewable. Previous code
 * reversed mapped/unmapped, which can cause Wine to discard/recreate
 * popup or transient windows when it checks window attributes.
 */
final class X11MapState {
    static final int IS_UNMAPPED=0;
    static final int IS_UNVIEWABLE=1;
    static final int IS_VIEWABLE=2;

    static byte of(boolean mapped, boolean ancestorsAndSelfViewable) {
        return (byte)(!mapped ? IS_UNMAPPED :
                ancestorsAndSelfViewable ? IS_VIEWABLE : IS_UNVIEWABLE);
    }

    private X11MapState() {}
}
