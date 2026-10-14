package com.drnas.console;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

/** Fixed viewport: follows new lines until the user scrolls back to read. */
final class StableLogView extends ScrollView {
    private final TextView text;
    private String shown="",latest="";
    private int desiredY;
    private boolean following=true, touching;

    StableLogView(Context context,int color) {
        super(context);
        text=new TextView(context);
        text.setTextColor(color);text.setTextSize(12);
        text.setTypeface(Typeface.MONOSPACE);
        int pad=Math.round(10*getResources().getDisplayMetrics().density);
        text.setPadding(pad,pad,pad,pad);
        text.setFocusable(false);text.setFocusableInTouchMode(false);
        setFocusable(false);setFillViewport(false);
        addView(text);
    }
    boolean isFollowing(){return following;}
    void setSnapshot(String snapshot) {
        latest=snapshot;
        if(snapshot.equals(shown))return;
        int y=getScrollY();
        Layout layout=text.getLayout();
        if(!following && !shown.isEmpty() && !snapshot.isEmpty() && !snapshot.startsWith(shown)) {
            String prefix=snapshot.substring(0,Math.min(128,snapshot.length()));
            int removed=shown.indexOf(prefix);
            if(removed<0)return;
            if(removed>0 && layout!=null) {
                int first=layout.getLineStart(layout.getLineForVertical(Math.max(0,y-text.getPaddingTop())));
                if(first<removed)return;
                y=Math.max(0,y-layout.getLineTop(layout.getLineForOffset(removed)));
            }
        }
        if(snapshot.isEmpty()){y=0;following=true;}
        desiredY=y;shown=snapshot;text.setText(snapshot);
        // onLayout runs after TextView has measured its new height.
        requestLayout();
    }
    void showLatest() {
        following=true;shown=latest;text.setText(latest);requestLayout();
    }
    void restorePosition(int y,boolean follow) {
        following=follow;desiredY=Math.max(0,y);requestLayout();
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        super.onLayout(changed,l,t,r,b);
        if(touching)return;
        // Local scrollTo does not move the outer page or steal keyboard focus.
        scrollTo(0,following?Math.max(0,text.getHeight()-getHeight()+getPaddingTop()+getPaddingBottom()):desiredY);
    }
    @Override protected void onScrollChanged(int l,int t,int oldl,int oldt) {
        super.onScrollChanged(l,t,oldl,oldt);
        desiredY=t;
        if(touching)following=atBottom();
        else if(!following && atBottom())following=true;
    }
    private boolean atBottom() {
        return getScrollY()+getHeight()-getPaddingTop()-getPaddingBottom()>=text.getHeight()-4;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        holdParent(event);return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        holdParent(event);return super.onTouchEvent(event);
    }
    private void holdParent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){touching=true;following=false;}
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){touching=false;following=atBottom();}
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(touching);
        desiredY=getScrollY();
    }
    @Override public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rect,boolean immediate) {
        return false;
    }
}
