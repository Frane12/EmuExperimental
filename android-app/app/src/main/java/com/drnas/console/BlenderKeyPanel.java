package com.drnas.console;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Movable, collapsible Blender shortcuts. Modifiers send real X11 key events. */
final class BlenderKeyPanel extends LinearLayout {
    interface KeySink {void send(int key,boolean pressed);}
    interface MouseSink {void send(int button,boolean pressed);}
    private final KeySink sink;
    private final MouseSink mouse;
    private final SharedPreferences prefs;
    private final LinearLayout rows;
    private final HorizontalScrollView keyStrip;
    private final Button toggle;
    private final Map<Integer,Button> held=new LinkedHashMap<>();
    private boolean expanded;
    private float dragX,dragY,startX,startY;
    private boolean dragging;
    private final View.OnLayoutChangeListener parentResize=(view,left,top,right,bottom,
            oldLeft,oldTop,oldRight,oldBottom)->{
        if(right-left!=oldRight-oldLeft || bottom-top!=oldBottom-oldTop)
            post(this::restoreWhenMeasured);
    };
    private final Runnable keyboard,options;

    BlenderKeyPanel(Context context,KeySink sink,MouseSink mouse,
                    Runnable keyboard,Runnable options) {
        super(context);this.sink=sink;this.mouse=mouse;
        this.keyboard=keyboard;this.options=options;
        prefs=context.getSharedPreferences("blender-shortcuts",Context.MODE_PRIVATE);
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(3),dp(3),dp(3),dp(3));
        setBackground(glass(0x55202429));
        Button grip=button("⠿","Povuci za pomicanje prečaca");
        addView(grip,new LayoutParams(dp(24),dp(34)));
        grip.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging=true;
                    dragX=e.getRawX();dragY=e.getRawY();startX=getX();startY=getY();return true;
                case MotionEvent.ACTION_MOVE:
                    place(startX+e.getRawX()-dragX,startY+e.getRawY()-dragY);return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging=false;
                    savePosition();return true;
                default:return true;
            }
        });
        rows=new LinearLayout(context);rows.setOrientation(VERTICAL);
        LinearLayout top=row(),bottom=row();
        key(top,"Tab",KeyEvent.KEYCODE_TAB);
        key(top,"G",KeyEvent.KEYCODE_G);key(top,"R",KeyEvent.KEYCODE_R);key(top,"S",KeyEvent.KEYCODE_S);
        gap(top);key(top,"X",KeyEvent.KEYCODE_X);key(top,"Y",KeyEvent.KEYCODE_Y);key(top,"Z",KeyEvent.KEYCODE_Z);
        gap(top);key(top,"E",KeyEvent.KEYCODE_E);key(top,"B",KeyEvent.KEYCODE_B);key(top,"A",KeyEvent.KEYCODE_A);
        key(top,"F3",KeyEvent.KEYCODE_F3);key(top,"Space",KeyEvent.KEYCODE_SPACE);
        modifier(bottom,"Shift",KeyEvent.KEYCODE_SHIFT_LEFT);
        modifier(bottom,"Ctrl",KeyEvent.KEYCODE_CTRL_LEFT);
        modifier(bottom,"Alt",KeyEvent.KEYCODE_ALT_LEFT);
        gap(bottom);key(bottom,"Esc",KeyEvent.KEYCODE_ESCAPE);
        key(bottom,"Enter",KeyEvent.KEYCODE_ENTER);key(bottom,"Del",KeyEvent.KEYCODE_FORWARD_DEL);
        action(bottom,"Undo",()->chord(KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_Z));
        action(bottom,"Save",()->chord(KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_S));
        action(bottom,"⌨",keyboard);
        // The top row is intentionally wide on a phone. Limit the panel to
        // the visible viewport and scroll the *keys*, not the grip/menu.
        // This keeps every shortcut usable without pushing the panel offscreen.
        keyStrip=new HorizontalScrollView(context);
        keyStrip.setFillViewport(false);
        keyStrip.setHorizontalScrollBarEnabled(false);
        keyStrip.setOverScrollMode(View.OVER_SCROLL_NEVER);
        keyStrip.setClipToPadding(true);
        keyStrip.addView(rows,new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        addView(keyStrip,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        // X11 button numbers 1=left, 3=right. Keep these buttons visible
        // even when the shortcut strip is collapsed and leave ordinary
        // touchscreen taps on ScreenView untouched.
        mouseButton("LMB","Lijevi klik miša",1);
        mouseButton("RMB","Desni klik miša",3);
        toggle=button("⌄","Sakrij ili prikaži Blender prečace");
        addView(toggle,new LayoutParams(dp(30),dp(34)));
        toggle.setOnClickListener(v->{releaseAll();setExpanded(!expanded);});
        Button menu=button("⋮","Izbornik, tipkovnica i test performansi");
        addView(menu,new LayoutParams(dp(26),dp(34)));
        menu.setOnClickListener(v->{releaseAll();options.run();});
        setExpanded(prefs.getBoolean("expanded",true));
        // Do not restore here: r30 hides the panel with GONE until the Blender
        // primary window maps. At that point getHeight() may still be ZERO.
        // Using height=0 placed the entire key panel below the bottom edge.
        // Restore only after visibility/layout provides real dimensions.
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(getParent() instanceof View)
            ((View)getParent()).addOnLayoutChangeListener(parentResize);
        post(this::restoreWhenMeasured);
    }
    @Override protected void onDetachedFromWindow(){
        if(getParent() instanceof View)
            ((View)getParent()).removeOnLayoutChangeListener(parentResize);
        super.onDetachedFromWindow();
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        if(w>0&&h>0)post(this::restoreWhenMeasured);
    }
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        // FrameLayout measures its WRAP_CONTENT child with AT_MOST on-screen
        // width; the two key rows can exceed that width in landscape phones.
        // Reserve room for grip, toggle, menu and margins. Only the key
        // strip scrolls horizontally; the rest always stays on screen.
        int mode=MeasureSpec.getMode(widthMeasureSpec);
        if(mode!=MeasureSpec.UNSPECIFIED) {
            int available=MeasureSpec.getSize(widthMeasureSpec);
            // grip 24 + 2 mouse buttons 2*42 + collapse 30 + menu 26 +
            // panel padding/margins ~= 184dp. Scroll only keyboard keys.
            int scrollWidth=Math.max(dp(36),Math.min(dp(650),available-dp(184)));
            ViewGroup.LayoutParams strip=keyStrip.getLayoutParams();
            if(strip!=null)strip.width=scrollWidth;
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec);
    }
    private void restoreWhenMeasured() {
        if(dragging || getVisibility()!=VISIBLE || !(getParent() instanceof View))return;
        View parent=(View)getParent();
        if(parent.getWidth()<=0 || parent.getHeight()<=0 ||
                getWidth()<=0 || getHeight()<=0)return;
        // Preserve the saved proportional left/bottom anchors across screen
        // sizes, rotation and expanded/collapsed key rows.
        float x=prefs.getFloat("x",.01f)*parent.getWidth();
        float y=prefs.getFloat("y",1f)*parent.getHeight()-getHeight()-dp(8);
        place(x,y);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable glass(int color) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(7));d.setStroke(dp(1),0x306e7a88);return d;
    }
    private Button button(String text,String description) {
        Button b=new Button(getContext());b.setText(text);b.setContentDescription(description);
        b.setTextSize(11);b.setTextColor(0xddeef2f7);b.setAllCaps(false);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(4),0,dp(4),0);b.setBackground(glass(0x60303740));
        b.setFocusable(false);return b;
    }
    private void mouseButton(String title,String description,int x11Button) {
        Button b=button(title,description);
        b.setTextSize(10);
        LayoutParams params=new LayoutParams(dp(42),dp(34));
        params.leftMargin=dp(1);
        addView(b,params);
        // A deliberate one-tap click, without faking a MotionEvent in the
        // Blender framebuffer. The normal touch-left-click path still runs.
        b.setOnClickListener(v->{
            mouse.send(x11Button,true);
            mouse.send(x11Button,false);
        });
    }
    private LinearLayout row() {
        LinearLayout r=new LinearLayout(getContext());r.setOrientation(HORIZONTAL);
        rows.addView(r,new LayoutParams(LayoutParams.WRAP_CONTENT,dp(29)));return r;
    }
    private void gap(LinearLayout r){r.addView(new View(getContext()),new LayoutParams(dp(5),1));}
    private Button action(LinearLayout r,String label,Runnable run) {
        Button b=button(label,label);LayoutParams p=new LayoutParams(dp(label.length()>3?44:32),dp(27));
        p.setMargins(dp(1),dp(1),dp(1),dp(1));r.addView(b,p);b.setOnClickListener(v->run.run());return b;
    }
    private void key(LinearLayout r,String label,int code){action(r,label,()->{
        sink.send(code,true);sink.send(code,false);releaseAll();
    });}
    private void modifier(LinearLayout r,String label,int code) {
        Button b=action(r,label,()->{});
        b.setOnClickListener(v->{
            if(held.remove(code)!=null){sink.send(code,false);b.setBackground(glass(0x60303740));}
            else {held.put(code,b);sink.send(code,true);b.setBackground(glass(0xb0b97830));}
        });
    }
    private void chord(int modifier,int key) {
        releaseAll();sink.send(modifier,true);sink.send(key,true);
        sink.send(key,false);sink.send(modifier,false);
    }
    void releaseAll() {
        for(Map.Entry<Integer,Button> e:held.entrySet()) {
            sink.send(e.getKey(),false);e.getValue().setBackground(glass(0x60303740));
        }
        held.clear();
    }
    private void setExpanded(boolean value) {
        expanded=value;rows.setVisibility(value?VISIBLE:GONE);toggle.setText(value?"⌄":"⌨");
        prefs.edit().putBoolean("expanded",value).apply();
        post(this::restoreWhenMeasured);
    }
    private void place(float x,float y) {
        if(!(getParent() instanceof View))return;
        View parent=(View)getParent();
        if(parent.getWidth()<=0 || parent.getHeight()<=0)return;
        float inset=dp(4);
        float maxX=Math.max(inset,parent.getWidth()-getWidth()-inset);
        float maxY=Math.max(inset,parent.getHeight()-getHeight()-inset);
        setX(Math.max(inset,Math.min(x,maxX)));
        setY(Math.max(inset,Math.min(y,maxY)));
    }
    private void savePosition() {
        if(!(getParent() instanceof View))return;
        View parent=(View)getParent();
        prefs.edit().putFloat("x",getX()/Math.max(1,parent.getWidth()))
                .putFloat("y",(getY()+getHeight()+dp(8))/Math.max(1,parent.getHeight())).apply();
    }
}
