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
    private final KeySink sink;
    private final SharedPreferences prefs;
    private final LinearLayout rows;
    private final Button toggle;
    private final Map<Integer,Button> held=new LinkedHashMap<>();
    private boolean expanded;
    private float dragX,dragY,startX,startY;
    private final Runnable keyboard,options;

    BlenderKeyPanel(Context context,KeySink sink,Runnable keyboard,Runnable options) {
        super(context);this.sink=sink;this.keyboard=keyboard;this.options=options;
        prefs=context.getSharedPreferences("blender-shortcuts",Context.MODE_PRIVATE);
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(3),dp(3),dp(3),dp(3));
        setBackground(glass(0x55202429));
        Button grip=button("⠿","Povuci za pomicanje prečaca");
        addView(grip,new LayoutParams(dp(24),dp(34)));
        grip.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragX=e.getRawX();dragY=e.getRawY();startX=getX();startY=getY();return true;
                case MotionEvent.ACTION_MOVE:
                    place(startX+e.getRawX()-dragX,startY+e.getRawY()-dragY);return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
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
        addView(rows,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        toggle=button("⌄","Sakrij ili prikaži Blender prečace");
        addView(toggle,new LayoutParams(dp(30),dp(34)));
        toggle.setOnClickListener(v->{releaseAll();setExpanded(!expanded);});
        Button menu=button("⋮","Izbornik, tipkovnica i test performansi");
        addView(menu,new LayoutParams(dp(26),dp(34)));
        menu.setOnClickListener(v->{releaseAll();options.run();});
        setExpanded(prefs.getBoolean("expanded",true));
        post(()->{
            if(!(getParent() instanceof View))return;
            View parent=(View)getParent();
            place(prefs.getFloat("x",.01f)*parent.getWidth(),
                    prefs.getFloat("y",1f)*parent.getHeight()-getHeight()-dp(8));
        });
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
        post(()->place(getX(),getY()));
    }
    private void place(float x,float y) {
        if(!(getParent() instanceof View))return;
        View parent=(View)getParent();
        setX(Math.max(0,Math.min(x,Math.max(0,parent.getWidth()-getWidth()))));
        setY(Math.max(0,Math.min(y,Math.max(0,parent.getHeight()-getHeight()))));
    }
    private void savePosition() {
        if(!(getParent() instanceof View))return;
        View parent=(View)getParent();
        prefs.edit().putFloat("x",getX()/Math.max(1,parent.getWidth()))
                .putFloat("y",(getY()+getHeight()+dp(8))/Math.max(1,parent.getHeight())).apply();
    }
}
