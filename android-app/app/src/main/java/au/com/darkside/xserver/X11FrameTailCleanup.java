package au.com.darkside.xserver;

/** Only full-origin, almost-full opaque frame replacements are allowed to
 * erase leftover rows/columns. Ordinary X11 partial updates MUST preserve
 * the pixels outside their damage rectangle.
 */
public final class X11FrameTailCleanup {
    public static boolean shouldScrub(int x,int y,int imageW,int imageH,
                                      int targetW,int targetH,boolean opaqueCopy) {
        if(!opaqueCopy || x!=0 || y!=0 || imageW<=0 || imageH<=0 ||
           imageW>targetW || imageH>targetH)return false;
        int remainderW=targetW-imageW;
        int remainderH=targetH-imageH;
        return (remainderW!=0 || remainderH!=0) &&
               remainderW<=64 && remainderH<=64 &&
               (long)imageW*100>=85L*targetW &&
               (long)imageH*100>=85L*targetH;
    }
    private X11FrameTailCleanup(){}
}
