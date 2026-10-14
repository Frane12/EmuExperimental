package com.drnas.console;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

/** Fixed-height log viewport. New output never requests focus or follows the tail. */
final class StableLogView extends ScrollView {
    private final TextView text;
    private String shown="",latest="";
    private int desiredY;
    private int revision;

    StableLogView(Context context,int color) {
        super(context);
        text=new TextView(context);
        text.setTextColor(color);text.setTextSize(12);
        text.setTypeface(Typeface.MONOSPACE);
        int pad=Math.round(10*getResources().getDisplayMetrics().density);
        text.setPadding(pad,pad,pad,pad);
        // Selectable TextViews can request focus and move ancestor ScrollViews.
        // Full text remains available through the existing TXT export.
        text.setFocusable(false);text.setFocusableInTouchMode(false);
        setFocusable(false);setFillViewport(false);
        addView(text);
    }
    void setSnapshot(String snapshot) {
        latest=snapshot;
        if(snapshot.equals(shown))return;
        int y=getScrollY();
        Layout layout=text.getLayout();
        if(!shown.isEmpty() && !snapshot.isEmpty() && !snapshot.startsWith(shown)) {
            String prefix=snapshot.substring(0,Math.min(128,snapshot.length()));
            int removed=shown.indexOf(prefix);
            if(removed<0)return; // Several ring-buffer trims: keep the reviewed snapshot.
            if(removed>0 && layout!=null) {
                int first=layout.getLineStart(layout.getLineForVertical(
                        Math.max(0,y-text.getPaddingTop())));
                // Retain the page being read if the backing ring buffer trimmed it.
                // The explicit Latest button can replace it with fresh output.
                if(first<removed)return;
                y=Math.max(0,y-layout.getLineTop(layout.getLineForOffset(removed)));
            }
        }
        if(snapshot.isEmpty())y=0;
        shown=snapshot;text.setText(snapshot);
        desiredY=y;
        final int ticket=++revision;
        post(()->{if(ticket==revision)scrollTo(0,desiredY);});
    }
    void showLatest() {
        shown=latest;text.setText(latest);
        ++revision;
        post(()->fullScroll(View.FOCUS_DOWN));
    }
    void restorePosition(int y) {desiredY=Math.max(0,y);post(()->scrollTo(0,desiredY));}
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        holdParent(event);return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        holdParent(event);return super.onTouchEvent(event);
    }
    private void holdParent(MotionEvent event) {
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(
                event.getActionMasked()!=MotionEvent.ACTION_UP &&
                event.getActionMasked()!=MotionEvent.ACTION_CANCEL);
        // A pending text-layout callback must use the user's current position.
        desiredY=getScrollY();
    }
    @Override public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rect,boolean immediate) {
        return false;
    }
}
