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
    private final Map<Integer,Button> heldMouse=new LinkedHashMap<>();
    private final MouseButtonHold mouseHold;
    private boolean expanded;
    private float dragX,dragY,startX,startY;
    private boolean dragging;
    private final View.OnLayoutChangeListener parentResize=(view,left,top,right,bottom,
            oldLeft,oldTop,oldRight,oldBottom)->{
        if(right-left!=oldRight-oldLeft || bottom-top!=oldBottom-oldTop)
            post(this::restoreWhenMeasured);
    };
    private final Runnable keyboard,options,exportToAndroid;

    BlenderKeyPanel(Context context,KeySink sink,MouseSink mouse,
                    Runnable keyboard,Runnable options,Runnable exportToAndroid) {
        super(context);this.sink=sink;this.mouse=mouse;
        mouseHold=new MouseButtonHold(mouse::send);
        setMotionEventSplittingEnabled(true);
        this.keyboard=keyboard;this.options=options;
        this.exportToAndroid=exportToAndroid;
        prefs=context.getSharedPreferences("blender-shortcuts",Context.MODE_PRIVATE);
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(7),dp(7),dp(7),dp(7));
        setBackground(panelGlass());setElevation(dp(7));
        Button grip=button("⠿","Drag to move the shortcut panel");
        dock(grip,32,48);
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
        key(top,"Edit Mode",KeyEvent.KEYCODE_TAB);
        key(top,"Move",KeyEvent.KEYCODE_G);key(top,"Rotate",KeyEvent.KEYCODE_R);key(top,"Scale",KeyEvent.KEYCODE_S);
        gap(top);key(top,"Axis X",KeyEvent.KEYCODE_X);key(top,"Axis Y",KeyEvent.KEYCODE_Y);key(top,"Axis Z",KeyEvent.KEYCODE_Z);
        gap(top);key(top,"Extrude",KeyEvent.KEYCODE_E);key(top,"Box Select",KeyEvent.KEYCODE_B);key(top,"Select All",KeyEvent.KEYCODE_A);
        key(top,"Search",KeyEvent.KEYCODE_F3);key(top,"Play/Pause",KeyEvent.KEYCODE_SPACE);
        modifier(bottom,"Shift",KeyEvent.KEYCODE_SHIFT_LEFT);
        modifier(bottom,"Ctrl",KeyEvent.KEYCODE_CTRL_LEFT);
        modifier(bottom,"Alt",KeyEvent.KEYCODE_ALT_LEFT);
        gap(bottom);key(bottom,"Cancel",KeyEvent.KEYCODE_ESCAPE);
        key(bottom,"Confirm",KeyEvent.KEYCODE_ENTER);key(bottom,"Delete",KeyEvent.KEYCODE_FORWARD_DEL);
        action(bottom,"Undo",()->chord(KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_Z));
        action(bottom,"Save",()->chord(KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_S));
        // Export is explicitly separate from Blender Ctrl+S: it opens the
        // Android system document picker only AFTER a project exists in Wine.
        action(bottom,"⇩ Android",exportToAndroid);
        action(bottom,"Keyboard",keyboard);
        // The top row is intentionally wide on a phone. Limit the panel to
        // the visible viewport and scroll the *keys*, not the grip/menu.
        // This keeps every shortcut usable without pushing the panel offscreen.
        keyStrip=new HorizontalScrollView(context);
        keyStrip.setFillViewport(false);
        keyStrip.setHorizontalScrollBarEnabled(false);
        keyStrip.setOverScrollMode(View.OVER_SCROLL_NEVER);
        keyStrip.setClipToPadding(true);
        keyStrip.setPadding(dp(3),0,dp(3),0);
        keyStrip.addView(rows,new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        addView(keyStrip,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        // X11 button numbers 1=left, 3=right. Keep these buttons visible
        // even when the shortcut strip is collapsed and leave ordinary
        // touchscreen taps on ScreenView untouched.
        mouseButton("LMB","Left mouse button",1);
        mouseButton("RMB","Right mouse button",3);
        toggle=button("⌄","Show or hide Blender shortcuts");
        toggle.setTextSize(19);dock(toggle,36,48);
        toggle.setOnClickListener(v->{releaseAll();setExpanded(!expanded);});
        Button menu=button("⋮","Menu, keyboard and performance test");
        menu.setTextSize(21);dock(menu,36,48);
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
        releaseAll();
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
            // Reserve the actual fixed controls and their margins. Bigger
            // labels scroll within the remaining width on small screens.
            int reserved=getPaddingLeft()+getPaddingRight();
            for(int i=0;i<getChildCount();i++) {
                View child=getChildAt(i);
                if(child==keyStrip || child.getVisibility()==GONE)continue;
                LayoutParams p=(LayoutParams)child.getLayoutParams();
                reserved+=Math.max(0,p.width)+p.leftMargin+p.rightMargin;
            }
            int scrollWidth=Math.max(0,Math.min(dp(760),available-reserved));
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
    // Hotfix 1.1: background alpha reduced by 10%; labels remain fully opaque.
    private GradientDrawable panelGlass() {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xa31c3042,0x8a101825});
        d.setCornerRadius(dp(18));d.setStroke(dp(1),0x7095c5db);return d;
    }
    private GradientDrawable keyGlass(boolean active) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                active?new int[]{0xcc3b8295,0xb9265d73}:
                        new int[]{0x93445d72,0x73233247});
        d.setCornerRadius(dp(10));d.setStroke(dp(1),active?0xd39eeaf1:0x759bbace);
        return d;
    }
    private android.graphics.drawable.Drawable keyBackground() {
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated},keyGlass(true));
        states.addState(new int[]{},keyGlass(false));
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x5078dce8),states,null);
    }
    private void dock(Button button,int width,int height) {
        LayoutParams p=new LayoutParams(dp(width),dp(height));
        p.setMargins(dp(2),dp(2),dp(2),dp(2));addView(button,p);
    }
    private Button button(String text,String description) {
        Button b=new Button(getContext());b.setText(text);b.setContentDescription(description);
        b.setTextSize(13);b.setTextColor(0xfff2f8ff);b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        b.setSingleLine(true);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(10),0,dp(10),0);b.setBackground(keyBackground());b.setElevation(dp(2));
        android.animation.StateListAnimator feedback=new android.animation.StateListAnimator();
        android.animation.AnimatorSet down=new android.animation.AnimatorSet();
        down.playTogether(android.animation.ObjectAnimator.ofFloat(b,"scaleX",.96f),
                android.animation.ObjectAnimator.ofFloat(b,"scaleY",.96f));down.setDuration(90);
        android.animation.AnimatorSet up=new android.animation.AnimatorSet();
        up.playTogether(android.animation.ObjectAnimator.ofFloat(b,"scaleX",1f),
                android.animation.ObjectAnimator.ofFloat(b,"scaleY",1f));up.setDuration(140);
        feedback.addState(new int[]{android.R.attr.state_pressed},down);
        feedback.addState(new int[]{},up);b.setStateListAnimator(feedback);
        b.setFocusable(false);return b;
    }
    private void mouseButton(String title,String description,int x11Button) {
        Button b=button(title,description);
        b.setTextSize(13);dock(b,56,48);
        heldMouse.put(x11Button,b);
        b.setContentDescription(description+" — hold while moving another finger to drag");
        // Preserve DOWN/MOVE/UP instead of synthesizing a full click on UP.
        // Disallow interception so sliding off the button still keeps the
        // press until UP or CANCEL. Split touches let another finger move
        // the framebuffer pointer independently.
        b.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.getParent().requestDisallowInterceptTouchEvent(true);
                mouseHold.press(x11Button);
                b.setPressed(true);b.setActivated(true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mouseHold.release(x11Button);
                b.setPressed(false);b.setActivated(false);
                v.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:return true;
            }
        });
        // Accessibility activation is still a complete click.
        b.setOnClickListener(v->{
            if(mouseHold.isHeld(x11Button))return;
            mouse.send(x11Button,true);
            mouse.send(x11Button,false);
        });
    }
    private LinearLayout row() {
        LinearLayout r=new LinearLayout(getContext());r.setOrientation(HORIZONTAL);
        rows.addView(r,new LayoutParams(LayoutParams.WRAP_CONTENT,dp(44)));return r;
    }
    private void gap(LinearLayout r){r.addView(new View(getContext()),new LayoutParams(dp(9),1));}
    private Button action(LinearLayout r,String label,Runnable run) {
        Button b=button(label,label);b.setMinimumWidth(dp(52));
        LayoutParams p=new LayoutParams(LayoutParams.WRAP_CONTENT,dp(40));
        p.setMargins(dp(3),dp(2),dp(3),dp(2));r.addView(b,p);b.setOnClickListener(v->run.run());return b;
    }
    private void key(LinearLayout r,String label,int code){action(r,label,()->{
        sink.send(code,true);sink.send(code,false);releaseAll();
    });}
    private void modifier(LinearLayout r,String label,int code) {
        Button b=action(r,label,()->{});
        b.setOnClickListener(v->{
            if(held.remove(code)!=null){sink.send(code,false);b.setActivated(false);}
            else {held.put(code,b);sink.send(code,true);b.setActivated(true);}
        });
    }
    private void chord(int modifier,int key) {
        releaseAll();sink.send(modifier,true);sink.send(key,true);
        sink.send(key,false);sink.send(modifier,false);
    }
    void releaseAll() {
        mouseHold.releaseAll();
        for(Button b:heldMouse.values()) {
            b.setPressed(false);b.setActivated(false);
        }
        for(Map.Entry<Integer,Button> e:held.entrySet()) {
            sink.send(e.getKey(),false);e.getValue().setActivated(false);
        }
        held.clear();
    }
    private void setExpanded(boolean value) {
        expanded=value;keyStrip.setVisibility(value?VISIBLE:GONE);toggle.setText(value?"⌄":"⌨");
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

