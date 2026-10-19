package au.com.darkside.xserver;

/** Bounded fallback for Wine's unmanaged Blender windows without owner hints. */
final class BlenderPopupPolicy {
    private BlenderPopupPolicy() {}
    /** Both managed (direct Wine launch) and unmanaged (Explorer desktop)
     * primary windows can finish startup. override_redirect is only relevant
     * to the separate, conservative popup-stacking fallback below. */
    static boolean startupPrimary(boolean mapped,boolean inputOnly,String title,
                                  int width,int height,int desktopWidth,int desktopHeight) {
        return mapped&&!inputOnly&&primary(title,width,height,desktopWidth,desktopHeight);
    }

    static <T> boolean moveAbove(java.util.List<T> order, T child, T owner) {
        int childIndex = order.indexOf(child), ownerIndex = order.indexOf(owner);
        if (child == owner || childIndex < 0 || ownerIndex < 0 || childIndex > ownerIndex)
            return false;
        order.remove(childIndex);
        order.add(order.indexOf(owner) + 1, child);
        return true;
    }

    static boolean primary(String title, int width, int height,
                           int desktopWidth, int desktopHeight) {
        if (title == null || width < 350 || height < 200) return false;
        if (title.contains(" - Blender ")) return true;
        // Blender initially gives both primary and auxiliary frames this name.
        // Only a nearly desktop-sized frame may be the owner at that stage.
        return title.equals("Blender") && desktopWidth > 0 && desktopHeight > 0 &&
                (long) width * 10 >= (long) desktopWidth * 9 &&
                (long) height * 10 >= (long) desktopHeight * 9;
    }

    static boolean auxiliary(String title, int width, int height,
                             int ownerWidth, int ownerHeight) {
        if (!("Blender".equals(title) || "Preferences".equals(title) ||
                "Blender Preferences".equals(title))) return false;
        return width >= 160 && height >= 100 && width <= ownerWidth &&
                height <= ownerHeight &&
                (long) width * height * 10 < (long) ownerWidth * ownerHeight * 9;
    }
}
