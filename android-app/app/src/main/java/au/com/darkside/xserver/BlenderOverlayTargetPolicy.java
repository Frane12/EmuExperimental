package au.com.darkside.xserver;

import java.util.List;

/** Chooses a topmost eligible sibling only after establishing one Blender client. */
final class BlenderOverlayTargetPolicy {
    interface Info<T> {
        boolean primary(T window);
        boolean eligible(T window);
        Object client(T window);
    }
    static <T> T choose(List<T> bottomToTop,Info<T> info) {
        Object owner=null;
        for(T window:bottomToTop) {
            if(!info.eligible(window) || !info.primary(window))continue;
            Object client=info.client(window);
            if(client==null)continue;
            if(owner!=null && owner!=client)return null;
            owner=client;
        }
        if(owner==null)return null;
        for(int i=bottomToTop.size()-1;i>=0;i--) {
            T window=bottomToTop.get(i);
            if(info.eligible(window) && info.client(window)==owner)return window;
        }
        return null;
    }
    // A click must focus the window under the pointer, never a different sibling.
    static <T> T atPointer(List<T> order, Info<T> info, T pointerTopLevel) {
        T blender=choose(order,info);
        return blender!=null && pointerTopLevel!=null &&
                info.eligible(pointerTopLevel) &&
                info.client(pointerTopLevel)==info.client(blender)?pointerTopLevel:null;
    }
    static boolean mayActivate(boolean keyboardGrab,boolean pointerGrab,
                               boolean buttonsHeld,boolean keysHeld) {
        return !keyboardGrab && !pointerGrab && !buttonsHeld && !keysHeld;
    }
    private BlenderOverlayTargetPolicy(){}
}
