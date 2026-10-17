package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Opt-in, touch-only movement stick for Wine games.
 *
 * Sends real X11 key press/release events (W/A/S/D) through ScreenView.
 * This deliberately does not masquerade as an XInput device: a virtual
 * Xbox controller needs a separate Wine HID/XInput bridge.
 *
 * No timer, polling thread, animations or per-frame allocations.
 */
public final class VirtualStickView extends View {
    public interface KeySink {
        void send(int androidKeyCode, boolean pressed);
    }

    private static final int[] KEYS = {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
    };
    private final boolean[] down = new boolean[4];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final KeySink sink;
    private int pointerId = -1;
    private float dx;
    private float dy;

    public VirtualStickView(Context context, KeySink sink) {
        super(context);
        this.sink = sink;
        setContentDescription("Virtual movement joystick. Sends WASD to the Windows game.");
        setFocusable(false);
        setClickable(true);
    }

    private float radius() {
        return Math.max(1f, Math.min(getWidth(), getHeight()) * 0.36f);
    }

    private void moveTo(float x, float y) {
        float r = radius();
        dx = Math.max(-1f, Math.min(1f, (x - getWidth() / 2f) / r));
        dy = Math.max(-1f, Math.min(1f, (y - getHeight() / 2f) / r));
        float magnitude = (float) Math.hypot(dx, dy);
        if (magnitude > 1f) {
            dx /= magnitude;
            dy /= magnitude;
        }
        // A small dead zone and axis hysteresis prevent keyboard chatter
        // from finger jitter, while diagonals still work.
        setKey(0, dy < (down[0] ? -0.16f : -0.28f)); // W
        setKey(1, dx < (down[1] ? -0.16f : -0.28f)); // A
        setKey(2, dy > (down[2] ?  0.16f :  0.28f)); // S
        setKey(3, dx > (down[3] ?  0.16f :  0.28f)); // D
        postInvalidateOnAnimation();
    }

    private void setKey(int index, boolean pressed) {
        if (down[index] == pressed) return;
        down[index] = pressed;
        sink.send(KEYS[index], pressed);
    }

    public void releaseAll() {
        pointerId = -1;
        dx = dy = 0f;
        for (int i = 0; i < KEYS.length; ++i) setKey(i, false);
        postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerId = event.getPointerId(index);
            moveTo(event.getX(index), event.getY(index));
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            int i = event.findPointerIndex(pointerId);
            if (i >= 0) moveTo(event.getX(i), event.getY(i));
            else releaseAll();
            return true;
        }
        if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL ||
                (action == MotionEvent.ACTION_POINTER_UP &&
                        event.getPointerId(index) == pointerId)) {
            releaseAll();
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = radius();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x64132336);
        canvas.drawCircle(cx, cy, r + 14f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        paint.setColor(0xB6B9EDFF);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD542D9FA);
        canvas.drawCircle(cx + dx*r, cy + dy*r, r*0.35f, paint);
    }
}
