package au.com.darkside.xserver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.os.Build;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.Instrumentation;
import android.os.Looper;
import android.os.Handler;
import android.view.inputmethod.InputMethodManager;
import android.app.Service;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.nio.charset.StandardCharsets;
import java.lang.Math; 

/**
 * This class implements an X Windows screen.
 * <p>
 * It also implements the screen's root window.
 * </p>
 *
 * @author Matthew Kwan
 */
public class ScreenView extends View {

    private interface PendingEvent {
        public void run();
    }

    private interface PendingPointerEvent extends PendingEvent {
    }

    private class PendingGrabButtonNotify implements PendingPointerEvent {

        private Window mWindow;
        private boolean mPressed;
        private int mMotionX;
        private int mMotionY;
        private int mButton;
        private int mGrabEventMask;
        private int mEventState;
        private Client mGrabPointerClient;
        private boolean mGrabPointerOwnerEvents;

        public PendingGrabButtonNotify(Window w, boolean pressed, int motionX, int motionY, int button, int grabEventMask, Client grabPointerClient, boolean grabPointerOwnerEvents) {
            mWindow = w;
            mPressed = pressed;
            mMotionX = motionX;
            mMotionY = motionY;
            mButton = button;
            mEventState = X11ButtonState.beforeTransition(_buttons, button);
            mGrabEventMask = grabEventMask;
            mGrabPointerClient = grabPointerClient;
            mGrabPointerOwnerEvents = grabPointerOwnerEvents;
        }

        public void run() {
            callGrabButtonNotify(mWindow, mPressed, mMotionX, mMotionY, mButton, mGrabEventMask, mGrabPointerClient, mGrabPointerOwnerEvents, mEventState);
        }
    }

    private class PendingGrabMotionNotify implements PendingPointerEvent {

        private Window mWindow;
        private int mX;
        private int mY;
        private int mButtons;
        private int mGrabEventMask;
        private Client mGrabPointerClient;
        private boolean mGrabPointerOwnerEvents;

        public PendingGrabMotionNotify(Window w, int x, int y, int buttons, int grabEventMask, Client grabPointerClient, boolean grabPointerOwnerEvents) {
            mWindow = w;
            mX = x;
            mY = y;
            mButtons = buttons;
            mGrabEventMask = grabEventMask;
            mGrabPointerClient = grabPointerClient;
            mGrabPointerOwnerEvents = grabPointerOwnerEvents;
        }

        public void run() {
            callGrabMotionNotify(mWindow, mX, mY, mButtons, mGrabEventMask, mGrabPointerClient, mGrabPointerOwnerEvents);
        }
    }

    private interface PendingKeyboardEvent extends PendingEvent {
    }

    private class PendingGrabKeyNotify implements PendingKeyboardEvent {

        private Window mWindow;
        private boolean mPressed;
        private int mMotionX;
        private int mMotionY;
        private int mKeycode;
        private Client mGrabKeyboardClient;
        private boolean mGrabKeyboardOwnerEvents;

        public PendingGrabKeyNotify(Window w, boolean pressed, int motionX, int motionY, int keycode, Client grabKeyboardClient, boolean grabKeyboardOwnerEvents) {
            mWindow = w;
            mPressed = pressed;
            mMotionX = motionX;
            mMotionY = motionY;
            mKeycode = keycode;
            mGrabKeyboardClient = grabKeyboardClient;
            mGrabKeyboardOwnerEvents = grabKeyboardOwnerEvents;
        }

        public void run() {
            callGrabKeyNotify(mWindow, mPressed, mMotionX, mMotionY, mKeycode, mGrabKeyboardClient, mGrabKeyboardOwnerEvents);
        }
    }

    private static class PendingEventQueue<T extends PendingEvent> {

        private Queue<T> mQueue = new LinkedList<T>();

        public void add(T event) {
            if (mQueue.offer(event)) {
                return;
            }
        }

        public T next() {
            return mQueue.poll();
        }
    }

    private final XServer _xServer;
    private final int _rootId;
    private Window _rootWindow = null;
    private Window _sharedClipboardWindow = null;
    private Property _sharedClipboardProperty = null;
    private Property _sharedClipboardPrimaryProperty = null;
    private Colormap _defaultColormap = null;
    private final Vector<Colormap> _installedColormaps;
    private final float _pixelsPerMillimeter;
    // X11 advertises a stable guest framebuffer independently of Android's physical
    // view size. Set once before clients connect; later inset/IME changes must not
    // re-create the live X11 root window.
    private int _guestWidth;
    private int _guestHeight;
    // Non-uniform Android scaling only; Wine/X11 stays 1280x720.
    private volatile boolean stretchFullscreen=true;
    private volatile DrnasScreenTransform cachedTransform;
    public void setStretchFullscreen(boolean enabled) {
        stretchFullscreen=enabled;
        recalculateScreenTransform();
        postInvalidateOnAnimation();
    }
    public boolean isStretchFullscreen(){return stretchFullscreen;}
    private void recalculateScreenTransform() {
        cachedTransform=DrnasScreenTransform.forSize(getWidth(),getHeight(),
                getGuestWidth(),getGuestHeight(),stretchFullscreen);
    }
    private DrnasScreenTransform screenTransform() {
        DrnasScreenTransform t=cachedTransform;
        if(t==null) {
            recalculateScreenTransform();
            t=cachedTransform;
        }
        return t;
    }
    public void setGuestResolution(int width,int height) {
        if (_rootWindow != null) throw new IllegalStateException("X11 screen already started");
        if (width < 640 || height < 480 || width > 4096 || height > 2160)
            throw new IllegalArgumentException("Unsupported guest resolution");
        _guestWidth=width;
        _guestHeight=height;
        recalculateScreenTransform();
    }
    public int getGuestWidth() {return _guestWidth>0?_guestWidth:getWidth();}
    public int getGuestHeight() {return _guestHeight>0?_guestHeight:getHeight();}
    private int guestX(float physicalX){return screenTransform().guestX(physicalX);}
    private int guestY(float physicalY){return screenTransform().guestY(physicalY);}


    // Laptop-style touchpad: one-finger movement is relative, not a warp to
    // the finger's absolute X11 coordinates. Tap clicks at the cursor.
    // Optional camera touch delta mode; Wine remains responsible for pointer grab.
    // This is X11 relative-pointer motion, NOT a raw-input HID device.
    private volatile boolean _cameraTouchMode;
    private volatile float _cameraSensitivity=1.65f;
    public void setCameraTouchMode(boolean enabled) { _cameraTouchMode=enabled; }
    public boolean isCameraTouchMode() { return _cameraTouchMode; }
    public void setCameraSensitivity(float value) {
        _cameraSensitivity=Math.max(0.4f,Math.min(4f,value));
    }
    private boolean _relativeTouchMode;
    private final X11DisplayRate displayRate=new X11DisplayRate();
    public float getDisplayedFps(){return fpsCounterEnabled?displayRate.sample(System.nanoTime()):0f;}
    public float getDisplayedFrameMs(){float fps=displayRate.current();return fpsCounterEnabled&&fps>0?1000f/fps:0f;}
    private boolean fpsCounterEnabled;
    public void setFpsCounterEnabled(boolean enabled) {
        fpsCounterEnabled=enabled;
        displayRate.reset(enabled?System.nanoTime():0L);
    }

    private float _touchLastX, _touchLastY, _touchRemainderX, _touchRemainderY;
    private float _touchTravel;
    private long _touchStartedAt;
    private boolean _touchTwoFingers, _touchDragging;
    private int _overlayButtons;
    private int _hardwareMouseButtons;
    private static final int[] HARDWARE_MOUSE_BUTTONS={MotionEvent.BUTTON_PRIMARY,
            MotionEvent.BUTTON_TERTIARY,MotionEvent.BUTTON_SECONDARY};
    private boolean _touchUsedOverlay;

    // Blender's first click must never acquire the global X11 monitor on
    // Android's main looper. Queue all pointer and keyboard events in FIFO order.
    private volatile boolean nonBlockingInputEnabled;
    private java.util.concurrent.ThreadPoolExecutor inputExecutor;
    private volatile Thread inputWorker;
    private final java.util.concurrent.atomic.AtomicLong inputEventSerial=
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger inputStalls=
            new java.util.concurrent.atomic.AtomicInteger();

    /** Enable only for the experimental Blender profile; other apps unchanged. */
    public void setNonBlockingInputEnabled(boolean enabled) {
        if (enabled == nonBlockingInputEnabled) return;
        if (enabled) {
            inputExecutor=new java.util.concurrent.ThreadPoolExecutor(1,1,0,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(192),task->{
                        Thread thread=new Thread(task,"drnas-x11-input");
                        thread.setDaemon(true);
                        inputWorker=thread;
                        return thread;
                    },new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            inputExecutor.prestartAllCoreThreads();
            nonBlockingInputEnabled=true;
            _xServer.diagnostic("DRNAS_X11_INPUT_ASYNC enabled=true queue=192 ordered=FIFO");
        } else {
            nonBlockingInputEnabled=false;
            if(inputExecutor!=null)inputExecutor.shutdownNow();
            inputExecutor=null;
        }
    }
    private boolean onAndroidUiThread() {
        return android.os.Looper.myLooper()==android.os.Looper.getMainLooper();
    }
    private boolean queueInput(String label,Runnable action) {
        java.util.concurrent.ThreadPoolExecutor executor=inputExecutor;
        if(executor==null)return false;
        final long serial=inputEventSerial.incrementAndGet();
        final java.util.concurrent.atomic.AtomicBoolean finished=
                new java.util.concurrent.atomic.AtomicBoolean();
        final long queuedAt=android.os.SystemClock.uptimeMillis();
        try {
            executor.execute(()->{
                inputStarted=serial;
                inputStartedAt=android.os.SystemClock.uptimeMillis();
                try {
                    action.run();
                } catch(RuntimeException error) {
                    _xServer.diagnostic("DRNAS_X11_INPUT_EXCEPTION seq="+serial+" "+
                            error.getClass().getSimpleName()+": "+error.getMessage());
                } finally {
                    inputCompleted=serial;
                    finished.set(true);
                    long delay=android.os.SystemClock.uptimeMillis()-queuedAt;
                    if(delay>600 && (serial<=4 || delay>2500))
                        _xServer.diagnostic("DRNAS_X11_INPUT_SLOW seq="+serial+
                                " label="+label+" elapsedMs="+delay);
                    else if(serial<=24)
                        _xServer.diagnostic("DRNAS_X11_INPUT_DONE seq="+serial+
                                " label="+label+" elapsedMs="+delay);
                }
            });
        } catch(java.util.concurrent.RejectedExecutionException rejected) {
            _xServer.diagnostic("DRNAS_X11_INPUT_QUEUE_OVERFLOW label="+label+
                    " pending="+executor.getQueue().size()+
                    " (Android UI remains responsive; reset session)");
            return false;
        }
        if(serial<=24)_xServer.diagnostic("DRNAS_X11_INPUT_QUEUED seq="+serial+" label="+label);
        // Never waits for the worker. Capture its stack if X11 stalls; keep
        // the Android UI, controls and log export responsive.
        if(serial<=24 || label.startsWith("button") || label.startsWith("key")) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                if(finished.get() || !nonBlockingInputEnabled)return;
                if(inputStalls.incrementAndGet()>5)return;
                Thread worker=inputWorker;
                StackTraceElement[] stack=worker==null?
                        new StackTraceElement[0]:worker.getStackTrace();
                StringBuilder trace=new StringBuilder();
                for(int i=0;i<Math.min(9,stack.length);i++)
                    trace.append(" | ").append(stack[i].toString());
                _xServer.diagnostic("DRNAS_X11_INPUT_STALL seq="+serial+
                        " label="+label+" waitedMs="+
                        (android.os.SystemClock.uptimeMillis()-queuedAt)+
                        " workerState="+(worker==null?"none":worker.getState())+
                        " queued="+executor.getQueue().size()+
                        " stack="+trace);
            },2500);
        }
        return true;
    }

    /** Overlay presses are independent of tap-to-click touchpad gestures. */
    public void setOverlayPointerButton(int button,boolean pressed) {
        if(nonBlockingInputEnabled && onAndroidUiThread()) {
            queueInput("button_"+button+(pressed?"_press":"_release"),
                    ()->setOverlayPointerButton(button,pressed));
            return;
        }
        synchronized(_xServer) {
            if(_rootWindow==null)return;
            if(pressed) {
                _overlayButtons|=1<<button;
                _touchUsedOverlay=true;
                if(button==1)_touchDragging=false;
            } else _overlayButtons&=~(1<<button);
            updatePointerButtons(button,pressed || (_hardwareMouseButtons&(1<<button))!=0);
        }
    }

    // A USB/Bluetooth mouse must bypass the relative touchpad recognizer:
    // that recognizer generates tap clicks on UP, not held mouse strokes.
    private boolean handleHardwareMouse(MotionEvent event) {
        int action=event.getActionMasked();
        updatePointerPosition(guestX(event.getX()),guestY(event.getY()),0);
        int state=event.getButtonState();
        if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP)state=0;
        if(action==MotionEvent.ACTION_BUTTON_RELEASE)state&=~event.getActionButton();
        if(action==MotionEvent.ACTION_BUTTON_PRESS)state|=event.getActionButton();
        if(action==MotionEvent.ACTION_DOWN && state==0)state=MotionEvent.BUTTON_PRIMARY;
        for(int button=1;button<=3;button++) {
            int bit=1<<button;
            boolean pressed=(state&HARDWARE_MOUSE_BUTTONS[button-1])!=0;
            if(pressed==((_hardwareMouseButtons&bit)!=0))continue;
            if(pressed)_hardwareMouseButtons|=bit;
            else _hardwareMouseButtons&=~bit;
            updatePointerButtons(button,pressed || (_overlayButtons&bit)!=0);
        }
        if(action==MotionEvent.ACTION_SCROLL) {
            float scroll=event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            int button=scroll>0?4:5;
            int clicks=Math.min(20,Math.round(Math.abs(scroll)));
            for(int i=0;i<clicks;i++) {
                updatePointerButtons(button,true);updatePointerButtons(button,false);
            }
        }
        return true;
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if(nonBlockingInputEnabled && onAndroidUiThread() &&
                event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            MotionEvent owned=MotionEvent.obtain(event);
            if(!queueInput("mouse_"+event.getActionMasked(),()->{
                try {onGenericMotionEvent(owned);} finally {owned.recycle();}
            }))owned.recycle();
            return true;
        }
        if(event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            synchronized(_xServer) {
                if(_rootWindow==null)return false;
                blank(false);
                return handleHardwareMouse(event);
            }
        }
        return super.onGenericMotionEvent(event);
    }

    public void cancelPointerGestures() {
        if(nonBlockingInputEnabled && onAndroidUiThread()) {
            queueInput("cancel_pointer",this::cancelPointerGestures);
            return;
        }
        synchronized(_xServer) {
            _overlayButtons=_hardwareMouseButtons=0;
            _touchDragging=false;_touchUsedOverlay=true;
            if(_rootWindow!=null)for(int button=1;button<=3;button++)
                updatePointerButtons(button,false);
        }
    }

    public void setRelativeTouchMode(boolean enabled) {
        _relativeTouchMode=enabled;
    }

    /** Called on the serial X11 input worker in the Blender profile. */
    private boolean handleTouchOnWorker(MotionEvent event) {
                synchronized (_xServer) {
                    if (_rootWindow == null) return false;
        
                    blank(false);    // Reset the screen saver.
                    if(event.isFromSource(android.view.InputDevice.SOURCE_MOUSE))
                        return handleHardwareMouse(event);
                    if (_relativeTouchMode) return handleRelativeTouch(event);
                    updatePointerPosition(guestX(event.getX()),guestY(event.getY()),0);
        
                    if(_enableTouchClicks){
                        if(event.getActionMasked() == MotionEvent.ACTION_DOWN && event.getActionIndex() == 0)
                            updatePointerButtons (1, true);
                        if((event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) && event.getActionIndex() == 0)
                            updatePointerButtons (1, false);
                        if(event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getActionIndex() == 1)
                            updatePointerButtons (3, true);
                        if((event.getActionMasked() == MotionEvent.ACTION_POINTER_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)  && (event.getActionIndex() == 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL))
                            updatePointerButtons (3, false);
                    }
                }

                if(event.getActionMasked() == MotionEvent.ACTION_DOWN){
                    _totalMove = 0;
                    _xPrec = event.getX();
                    _yPrec = event.getY();
                }
                else if (event.getActionMasked() == MotionEvent.ACTION_MOVE){
                    final double dx = event.getX() - _xPrec;
                    final double dy = event.getY() - _yPrec;
                    final double dl = Math.sqrt(dx * dx + dy * dy);
                    _totalMove += dl;
                    _xPrec = event.getX();
                    _yPrec = event.getY();
                }


                if(_totalMove < 20){ // -- workaround for phones with cheap touchscreens (which will constantly trigger ACTION_MOVE events)
                    _ignoreLongPress = false;
                    return false; // make longClick Listeners work!
                }

                _ignoreLongPress = true;
                return true;
    }

    private boolean handleRelativeTouch(MotionEvent event) {
        int action=event.getActionMasked();
        switch(action) {
        case MotionEvent.ACTION_DOWN:
            _touchLastX=event.getX(0);
            _touchLastY=event.getY(0);
            _touchStartedAt=event.getEventTime();
            _touchTravel=0f;
            _touchRemainderX=_touchRemainderY=0f;
            _touchTwoFingers=false;
            _touchDragging=false;
            _touchUsedOverlay=(_overlayButtons|_hardwareMouseButtons)!=0;
            return true;
        case MotionEvent.ACTION_POINTER_DOWN:
            _touchTwoFingers=true;
            return true;
        case MotionEvent.ACTION_MOVE:
            if(event.getPointerCount()!=1) {
                _touchTwoFingers=true;
                return true;
            }
            float x=event.getX(0),y=event.getY(0);
            float dx=x-_touchLastX,dy=y-_touchLastY;
            _touchLastX=x;
            _touchLastY=y;
            _touchTravel+=(float)Math.hypot(dx,dy);
            if(_touchTwoFingers)return true;
            // Hold the finger for 400 ms and begin a slow drag to press mouse
            // button one; keep it held while moving, release on finger up.
            // Fast swipes only move the pointer. A quick tap clicks instead.
            if(!_cameraTouchMode && !_touchUsedOverlay && !_touchDragging && event.getEventTime()-_touchStartedAt>=400 &&
                    _touchTravel<dpTouch(24)) {
                updatePointerButtons(1,true);
                _touchDragging=true;
            }
            DrnasScreenTransform t=screenTransform();
            float gain=_cameraTouchMode?_cameraSensitivity:1f;
            _touchRemainderX+=t.deltaX(dx)*gain;
            _touchRemainderY+=t.deltaY(dy)*gain;
            int stepX=(int)_touchRemainderX;
            int stepY=(int)_touchRemainderY;
            _touchRemainderX-=stepX;
            _touchRemainderY-=stepY;
            if(stepX!=0 || stepY!=0)
                updatePointerPosition(
                    Math.max(0,Math.min(getGuestWidth()-1,_currentCursorX+stepX)),
                    Math.max(0,Math.min(getGuestHeight()-1,_currentCursorY+stepY)),0);
            return true;
        case MotionEvent.ACTION_POINTER_UP:
            _touchTwoFingers=true;
            return true;
        case MotionEvent.ACTION_UP:
            if(_touchDragging) {
                updatePointerButtons(1,false);
            } else if(_touchUsedOverlay) {
                // The overlay owns its release. A short motion/tap here must
                // not release its LMB or synthesize an unrelated right click.
            } else if(_touchTwoFingers && !_cameraTouchMode) {
                updatePointerButtons(3,true);
                updatePointerButtons(3,false);
            } else if(!_cameraTouchMode && _touchTravel<dpTouch(15)) {
                int button=(event.getEventTime()-_touchStartedAt>=650)?3:1;
                updatePointerButtons(button,true);
                updatePointerButtons(button,false);
            }
            _touchDragging=false;
            return true;
        case MotionEvent.ACTION_CANCEL:
            if(_touchDragging)updatePointerButtons(1,false);
            _touchDragging=false;
            return true;
        default:
            return true;
        }
    }

    private float dpTouch(float size) {
        return size*getResources().getDisplayMetrics().density;
    }

    public interface CursorOverlayListener {
        void onCursorUpdated(Bitmap bitmap,int x,int y,int hotspotX,int hotspotY);
    }
    private volatile CursorOverlayListener cursorOverlayListener;
    public void setCursorOverlayListener(CursorOverlayListener listener) {
        cursorOverlayListener=listener;
    }
    private boolean compositorReported;

    private Cursor _currentCursor;
    private int _currentCursorX;
    private int _currentCursorY;
    private Cursor _drawnCursor = null;
    private int _drawnCursorX;
    private int _drawnCursorY;
    private Window _motionWindow = null;
    private int _motionX;
    private int _motionY;
    private int _buttons = 0;
    private boolean _isBlanked = false;
	private boolean	_arrowsAsButtons = false;
    private boolean	_inhibitBackButton = false;
    private boolean	_enableTouchClicks = true;
    private boolean _sharedClipboard = true;
    private Paint _paint;

    private Client _grabPointerClient = null;
    private Window _grabPointerWindow = null;
    private int _grabPointerTime = 0;
    private boolean _grabPointerOwnerEvents = false;
    private boolean _grabPointerSynchronous = false;
    private boolean _grabPointerPassive = false;
    private boolean _grabPointerAutomatic = false;
    private boolean _grabPointerFreezeNextEvent = false;
    private Client _grabKeyboardClient = null;
    private Window _grabKeyboardWindow = null;
    private int _grabKeyboardTime = 0;
    private boolean _grabKeyboardOwnerEvents = false;
    private boolean _grabKeyboardSynchronous = false;
    private boolean _grabKeyboardFreezeNextEvent = false;
    private Cursor _grabCursor = null;
    private Window _grabConfineWindow = null;
    private int _grabEventMask = 0;
    private PassiveKeyGrab _grabKeyboardPassiveGrab = null;

    private Window _focusWindow = null;
    private byte _focusRevertTo = 0;    // 0=None, 1=Root, 2=Parent.
    private int _focusLastChangeTime = 0;

    private PendingEventQueue<PendingPointerEvent> mPendingPointerEvents;
    private PendingEventQueue<PendingKeyboardEvent> mPendingKeyboardEvents;

    private boolean _ignoreLongPress = false;

    private static final int ACTION_CANCEL = 0;
    private static final int ACTION_CTRL_C = 1;
    private static final int ACTION_CTRL_V = 2;
    private static final int ACTION_CTRL_X = 3;
    private static final int ACTION_CTRL_A = 4;
    private static final int ACTION_R_CLICK = 5;
    private static final int ACTION_M_CLICK = 6;
    private static final int ACTION_ESC = 7;
    private static final int ACTION_KEYBOARD = 8;

    // -- helpers for movement thresholding, works around phones with cheap touch screens
    private double _totalMove = 0;
    private double _xPrec = 0;
    private double _yPrec = 0;

    /**
     * Constructor.
     *
     * @param c                   The application context.
     * @param xServer             The X server.
     * @param rootId              The ID of the root window, to be created later.
     * @param pixelsPerMillimeter Screen resolution.
     */
    public ScreenView(Context c, XServer xServer, int rootId, float pixelsPerMillimeter) {
        super(c);

        setFocusable(true);
        setFocusableInTouchMode(true);

        _xServer = xServer;
        _rootId = rootId;
        _installedColormaps = new Vector<Colormap>();
        _pixelsPerMillimeter = pixelsPerMillimeter;
        _paint = new Paint();

        mPendingPointerEvents = new PendingEventQueue<PendingPointerEvent>();
        mPendingKeyboardEvents = new PendingEventQueue<PendingKeyboardEvent>();

        // ---- Listeners for touch input ----
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // do nothing
            }
        });

        setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if(nonBlockingInputEnabled && onAndroidUiThread()) {
                    MotionEvent owned=MotionEvent.obtain(event);
                    if(!queueInput("touch_"+event.getActionMasked(),()->{
                        try {handleTouchOnWorker(owned);} finally {owned.recycle();}
                    }))owned.recycle();
                    return true;
                }
                return handleTouchOnWorker(event);
            }
        });

        setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(_ignoreLongPress)
                    return true;

                ActionMode.Callback cb = new ActionMode.Callback(){
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        menu.add(0, ACTION_CTRL_C, 0, "CTRL+C");
                        menu.add(0, ACTION_CTRL_V, 0, "CTRL+V");
                        menu.add(0, ACTION_CTRL_X, 0, "CTRL+X");
                        menu.add(0, ACTION_CTRL_A, 0, "CTRL+A");
                        menu.add(0, ACTION_ESC, 0, "ESC");
                        menu.add(0, ACTION_R_CLICK, 0, "M-Click");
                        menu.add(0, ACTION_R_CLICK, 0, "R-Click");
                        menu.add(0, ACTION_KEYBOARD, 0, "Keyboard");
                        menu.add(0, ACTION_CANCEL, 0, "Cancel");
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false; // Return false if nothing is done
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        switch (item.getItemId()) {
                            case ACTION_CTRL_C:
                                onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT));
                                onKeyDown(KeyEvent.KEYCODE_C, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C));
                                onKeyUp(KeyEvent.KEYCODE_C, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C));
                                onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT));
                                mode.finish();
                                return true;
                            case ACTION_CTRL_V:
                                if (_sharedClipboard) {
                                    Selection.setSelectionOwner(_xServer, _xServer.findAtom("CLIPBOARD"), _sharedClipboardWindow); // override owner to point to our clipboardwindow
                                    Selection.setSelectionOwner(_xServer, _xServer.findAtom("PRIMARY"), _sharedClipboardWindow);
                                    ClipboardManager clipboard = (ClipboardManager) _xServer.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData.Item clipitem = clipboard.getPrimaryClip().getItemAt(0);

                                    _sharedClipboardProperty.setData(clipitem.getText().toString());
                                    _sharedClipboardPrimaryProperty.setData(clipitem.getText().toString());
                                }
                            
                                onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT));
                                onKeyDown(KeyEvent.KEYCODE_V, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V));
                                onKeyUp(KeyEvent.KEYCODE_V, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V));
                                onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT));
                                mode.finish();
                                return true;
                            case ACTION_CTRL_X:
                                onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT));
                                onKeyDown(KeyEvent.KEYCODE_X, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X));
                                onKeyUp(KeyEvent.KEYCODE_X, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_X));
                                onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT));
                                mode.finish();
                                return true;
                            case ACTION_CTRL_A:
                                onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT));
                                onKeyDown(KeyEvent.KEYCODE_A, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A));
                                onKeyUp(KeyEvent.KEYCODE_A, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A));
                                onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT));
                                mode.finish();
                                return true;
                            case ACTION_R_CLICK:
                                if (_sharedClipboard) {
                                    Selection.setSelectionOwner(_xServer, _xServer.findAtom("CLIPBOARD"), _sharedClipboardWindow); // override owner to point to our clipboardwindow
                                    Selection.setSelectionOwner(_xServer, _xServer.findAtom("PRIMARY"), _sharedClipboardWindow);
                                    ClipboardManager clipboard = (ClipboardManager) _xServer.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData.Item clipitem = clipboard.getPrimaryClip().getItemAt(0);

                                    _sharedClipboardProperty.setData(clipitem.getText().toString());
                                    _sharedClipboardPrimaryProperty.setData(clipitem.getText().toString());
                                }
                                updatePointerButtons(3, true);
                                updatePointerButtons(3, false);
                                mode.finish();
                                return true;
                            case ACTION_M_CLICK:
                                if (_sharedClipboard) {
                                    Selection.setSelectionOwner(_xServer, _xServer.findAtom("CLIPBOARD"), _sharedClipboardWindow); // override owner to point to our clipboardwindow
                                    Selection.setSelectionOwner(_xServer, _xServer.findAtom("PRIMARY"), _sharedClipboardWindow);
                                    ClipboardManager clipboard = (ClipboardManager) _xServer.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData.Item clipitem = clipboard.getPrimaryClip().getItemAt(0);

                                    _sharedClipboardProperty.setData(clipitem.getText().toString());
                                    _sharedClipboardPrimaryProperty.setData(clipitem.getText().toString());
                                }
                                updatePointerButtons(2, true);
                                updatePointerButtons(2, false);
                                mode.finish();
                                return true;
                            case ACTION_ESC:
                                onKeyDown(KeyEvent.KEYCODE_ESCAPE, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
                                onKeyUp(KeyEvent.KEYCODE_ESCAPE, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
                                mode.finish();
                                return true;
                            case ACTION_KEYBOARD:
                                mode.finish();
                                postDelayed(() -> {
                                    requestFocus();
                                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if(imm!=null)imm.showSoftInput(ScreenView.this,InputMethodManager.SHOW_IMPLICIT);
                                },150);
                                return true;
                            case ACTION_CANCEL:
                                mode.finish();
                                return true;
                            default:
                                mode.finish();
                                return false;
                        }
                    }

                    // Called when the user exits the action mode
                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        mode = null;
                    } 
                };

                // use floating mode on newer android versions
                if(Build.VERSION.SDK_INT >= 23){
                    startActionMode(cb, ActionMode.TYPE_FLOATING);
                }
                else{
                    startActionMode(cb);
                }

                return false;
            }
        });
        requestFocus();
    }

    /**
     * needed make softkeyboard work in landscape mode and to capture backspace.
     */
    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs){
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return new android.view.inputmethod.BaseInputConnection(this,false) {
            @Override public boolean sendKeyEvent(KeyEvent event) {
                if(event.getAction()==KeyEvent.ACTION_DOWN)return onKeyDown(event.getKeyCode(),event);
                if(event.getAction()==KeyEvent.ACTION_UP)return onKeyUp(event.getKeyCode(),event);
                // BaseInputConnection emits ACTION_MULTIPLE for committed strings.
                // Translate those to real X11 key events, including modifier pairs.
                if(event.getAction()==KeyEvent.ACTION_MULTIPLE && event.getCharacters()!=null) {
                    android.view.KeyCharacterMap map=android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD);
                    KeyEvent[] events=map.getEvents(event.getCharacters().toCharArray());
                    if(events==null)return false;
                    for(KeyEvent key:events)sendKeyEvent(key);
                    return true;
                }
                return false;
            }
            @Override public boolean deleteSurroundingText(int before,int after) {
                if(before<0 || after<0 || before>256 || after>256)return false;
                if(getEditable()!=null && getEditable().length()>0)
                    return super.deleteSurroundingText(before,after);
                for(int i=0;i<before;i++)press(KeyEvent.KEYCODE_DEL);
                for(int i=0;i<after;i++)press(KeyEvent.KEYCODE_FORWARD_DEL);
                return true;
            }
            private void press(int code) {
                sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
                sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
            }
        };
    }
    

    /**
     * Placeholder constructor to prevent a compiler warning.
     *
     * @param c
     */
    public ScreenView(Context c) {
        super(c);

        _xServer = null;
        _rootId = 0;
        _installedColormaps = null;
        _pixelsPerMillimeter = 0;
    }

    /**
     * Return the screen's root window.
     *
     * @return The screen's root window.
     */
    public Window getRootWindow() {
        return _rootWindow;
    }

    /**
     * Return the screen's default colormap.
     *
     * @return The screen's default colormap.
     */
    public Colormap getDefaultColormap() {
        return _defaultColormap;
    }

    /**
     * Return the current cursor.
     *
     * @return The current cursor.
     */
    public Cursor getCurrentCursor() {
        return _currentCursor;
    }

    /**
     * Return the current pointer X coordinate.
     *
     * @return The current pointer X coordinate.
     */
    public int getPointerX() {
        return _currentCursorX;
    }

    /**
     * Return the current pointer Y coordinate.
     *
     * @return The current pointer Y coordinate.
     */
    public int getPointerY() {
        return _currentCursorY;
    }

    /**
     * Return a mask indicating the current state of the pointer and
     * modifier buttons.
     *
     * @return A mask indicating the current state of the buttons.
     */
    public int getButtons() {
        return _buttons;
    }

    /**
     * Return the window that has input focus. Can be null.
     *
     * @return The window that has input focus.
     */
    public Window getFocusWindow() {
        return _focusWindow;
    }

    /**
     * Blank/unblank the screen.
     *
     * @param flag If true, blank the screen. Otherwise unblank it.
     */
    public void blank(boolean flag) {
        if (_isBlanked == flag) return;

        _isBlanked = flag;
        postInvalidateOnAnimation();

        if (!_isBlanked) _xServer.resetScreenSaver();
    }

    /**
     * Add an installed colormap.
     *
     * @param cmap The colormap to add.
     */
    public void addInstalledColormap(Colormap cmap) {
        _installedColormaps.add(cmap);
        if (_defaultColormap == null) _defaultColormap = cmap;
    }

    /**
     * Remove an installed colormap.
     *
     * @param cmap The colormap to remove.
     */
    public void removeInstalledColormap(Colormap cmap) {
        _installedColormaps.remove(cmap);
        if (_defaultColormap == cmap) {
            if (_installedColormaps.size() == 0) _defaultColormap = null;
            else _defaultColormap = _installedColormaps.firstElement();
        }
    }

    /**
     * Remove all colormaps except the default one.
     */
    public void removeNonDefaultColormaps() {
        if (_installedColormaps.size() < 2) return;

        _installedColormaps.clear();
        if (_defaultColormap != null) _installedColormaps.add(_defaultColormap);
    }

    /**
     * Called when a window is deleted, usually due to a client disconnecting.
     * Removes all references to the window.
     *
     * @param w The window being deleted.
     */
    public void deleteWindow(Window w) {
        if (_grabPointerWindow == w || _grabConfineWindow == w) {
            _grabPointerClient = null;
            _grabPointerWindow = null;
            _grabCursor = null;
            _grabConfineWindow = null;
            updatePointer(2);
        } else {
            updatePointer(0);
        }

        revertFocus(w);
    }

    /**
     * Called when the window is unmapped.
     * If the window had keyboard focus, update the focus window.
     *
     * @param w
     */
    public void revertFocus(Window w) {
        traceInputState("revert_focus_window_"+w.getId());
        if (w == _grabKeyboardWindow) {
            Window pw = _rootWindow.windowAtPoint(_motionX, _motionY);

            Window.focusInOutNotify(_grabKeyboardWindow, _focusWindow, pw, _rootWindow, 2);
            _grabKeyboardClient = null;
            _grabKeyboardWindow = null;
        }

        if (w == _focusWindow) {
            Window pw = _rootWindow.windowAtPoint(_motionX, _motionY);

            if (_focusRevertTo == 0) {
                _focusWindow = null;
            } else if (_focusRevertTo == 1) {
                _focusWindow = _rootWindow;
            } else {
                _focusWindow = w.getParent();
                while (!_focusWindow.isViewable()) _focusWindow = _focusWindow.getParent();
            }

            _focusRevertTo = 0;
            Window.focusInOutNotify(w, _focusWindow, pw, _rootWindow, _grabKeyboardWindow == null ? 0 : 3);
        }
        traceInputState("revert_focus_done");
    }

    /**
     * Called when the view needs drawing.
     *
     * @param canvas The canvas on which the view will be drawn.
     */
    private boolean rendererErrorReported;
    private boolean rendererFallbackRequested;

    @Override
    protected void onDraw(Canvas canvas) {
        int checkpoint=canvas.save();
        try {
            drawDesktop(canvas);
        } catch(RuntimeException error) {
            if(!rendererErrorReported) {
                rendererErrorReported=true;
                com.drnas.console.AndroidCrashDiagnostics.recordRenderer(_xServer.getContext(),error);
                _xServer.diagnostic("Android X11 drawing FAILED: "+android.util.Log.getStackTraceString(error));
            }
            if(!rendererFallbackRequested) {
                rendererFallbackRequested=true;
                _xServer.diagnostic("Retrying Android desktop Canvas in CPU mode; Vulkan driver selection is unchanged.");
                post(()->{setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);invalidate();});
            }
            // Leave the Android controls alive even if CPU drawing also fails.
            // The retained report can be shared without restarting Wine.
        } finally {
            canvas.restoreToCount(checkpoint);
        }
    }

    private void drawDesktop(Canvas canvas) {
        if (_rootWindow == null) {
            super.onDraw(canvas);
            return;
        }

        synchronized (_xServer) {
            if (!compositorReported) {
                compositorReported=true;
                _xServer.diagnostic("Android X11 Canvas hardwareAccelerated="+
                        (isHardwareAccelerated() && canvas.isHardwareAccelerated())+
                        "; cursor isolated from CPU desktop redraw; Vulkan DRI3/Present not implemented");
            }
            if (_isBlanked) {
                canvas.drawColor(0xff000000);
                return;
            }

            _paint.reset();
            // GPU bilinear sampling improves noninteger full-screen scaling
            // without another X11 buffer copy/readback. Leave CPU fallback
            // on the cheaper nonfiltered path to avoid software penalties.
            if(stretchFullscreen && canvas.isHardwareAccelerated())
                _paint.setFilterBitmap(true);
            // The X11 framebuffer is unchanged. FILL scales X/Y independently
            // into the full Android View; FIT retains original letterboxing.
            int checkpoint=canvas.save();
            if (_guestWidth>0 && _guestHeight>0) {
                DrnasScreenTransform t=screenTransform();
                if(!t.fill)canvas.drawColor(0xff000000);
                canvas.translate(t.offsetX,t.offsetY);
                canvas.scale(t.scaleX,t.scaleY);
                canvas.clipRect(0,0,_guestWidth,_guestHeight);
            }
            _rootWindow.draw(canvas, _paint);
            // Alternative to the old framebuffer cleaners: a guarded Canvas
            // PRESENTATION mask. No guest pixels are ever zeroed/reallocated.
            int coveredRows=_xServer.getPresentationMaskRows();
            if(coveredRows>0 && coveredRows<=32) {
                _paint.setColor(0xff000000);
                _paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(0,getGuestHeight()-coveredRows,
                        getGuestWidth(),getGuestHeight(),_paint);
                _paint.reset();
            }
            // No timing/counters in HWUI's drawing path when HUD is disabled.
            if(fpsCounterEnabled)displayRate.draw();
            _xServer.captureCanvasDraw();
            _xServer.canvasDrawn();
            if (cursorOverlayListener==null && _currentCursor!=null)
                canvas.drawBitmap(_currentCursor.getBitmap(), _currentCursorX - _currentCursor.getHotspotX(), _currentCursorY - _currentCursor.getHotspotY(), null);
            canvas.restoreToCount(checkpoint);

            _drawnCursor = _currentCursor;
            _drawnCursorX = _currentCursorX;
            _drawnCursorY = _currentCursorY;
        }
    }

    /**
     * Called when the size changes.
     * Create the root window.
     *
     * @param width     The new width.
     * @param height    The new height.
     * @param oldWidth  The old width.
     * @param oldHeight The old height.
     */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        recalculateScreenTransform();

        // Android insets / keyboard can resize the physical View. Do not
        // destroy X11 root resources while Explorer has active client windows.
        if (_rootWindow != null) { postInvalidateOnAnimation(); return; }
        int rootWidth=getGuestWidth(),rootHeight=getGuestHeight();
        _rootWindow = new Window(_rootId, _xServer, null, this, null, 0, 0, rootWidth, rootHeight, 0, false, true);
        _sharedClipboardWindow = new Window(_xServer.nextFreeResourceId()+1, _xServer, null, this, _rootWindow, -1, -1, 1, 1, 0, true, false); // hidden window managing android <-> xServer clipboard
        _sharedClipboardWindow.setIsServerWindow(true); // flag as functional server only window (there is a urgent need to introduce interfaces..)
        _sharedClipboardProperty = new Property(_xServer.findAtom("CLIPBOARD").getId(), _xServer.findAtom("CLIPBOARD").getId(), (byte)32); // property which will hold the clipboard data
        _sharedClipboardPrimaryProperty = new Property(_xServer.findAtom("PRIMARY").getId(), _xServer.findAtom("PRIMARY").getId(), (byte)32); // property which will hold the clipboard data

        Property.OnPropertyChangedListener cb = new Property.OnPropertyChangedListener(){ // -- executed on a per client thread basis
            @Override
            public void onPropertyChanged(byte[] data, Atom type){
                switch(type.getName()){
                    case "UTF8_STRING":
                        String s = new String(data, StandardCharsets.UTF_8); // convert to UTF8 string

                        // create task for UI thread
                        class OneShotTask implements Runnable {
                            private String d;
                            OneShotTask(String s) { d = s; }
                            public void run() {
                                ClipboardManager clipboard = (ClipboardManager) _xServer.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("cb", d);
                                clipboard.setPrimaryClip(clip); // store to clipboard
                            }
                        }
                        new Handler(Looper.getMainLooper()).post(new OneShotTask(s));
                        break;
                    default: // different types can be implemented here (binary etc.)
                        break;
                }
            }
        };


        // capture data changes on this property
        _sharedClipboardProperty.setOnPropertyChangedListener(cb);
        _sharedClipboardPrimaryProperty.setOnPropertyChangedListener(cb);

        _sharedClipboardWindow.addProperty(_sharedClipboardPrimaryProperty);
        _sharedClipboardWindow.addProperty(_sharedClipboardProperty);

        _xServer.addResource(_rootWindow);
        _xServer.addResource(_sharedClipboardWindow);

        _currentCursor = _rootWindow.getCursor();
        _currentCursorX = rootWidth / 2;
        _currentCursorY = rootHeight / 2;
        _drawnCursorX = _currentCursorX;
        _drawnCursorY = _currentCursorY;
        _motionX = _currentCursorX;
        _motionY = _currentCursorY;
        _motionWindow = _rootWindow;
        _focusWindow = _rootWindow;

        // Everything set up, so start listening for clients.
        _xServer.start();
    }

    /**
     * Move the pointer on the screen.
     *
     * @param x      New X coordinate.
     * @param y      New Y coordinate.
     * @param cursor The cursor to draw.
     */
    public void invalidateGuestRect(int left,int top,int right,int bottom) {
        // Ignore empty and fully off-screen X11 damage instead of waking HWUI
        // for popups at negative coordinates or windows outside the viewport.
        // Keep the existing vsync-coalesced postInvalidateOnAnimation path.
        if(right<=left || bottom<=top) return;
        if(_guestWidth<=0 || _guestHeight<=0) {
            postInvalidateOnAnimation(left,top,right,bottom);
            return;
        }
        int x0=Math.max(0,left), y0=Math.max(0,top);
        int x1=Math.min(_guestWidth,right), y1=Math.min(_guestHeight,bottom);
        if(x0>=x1 || y0>=y1) return;
        DrnasScreenTransform t=screenTransform();
        postInvalidateOnAnimation((int)Math.floor(t.physicalX(x0))-3,
                       (int)Math.floor(t.physicalY(y0))-3,
                       (int)Math.ceil(t.physicalX(x1))+3,
                       (int)Math.ceil(t.physicalY(y1))+3);
    }

    private void movePointer(int x, int y, Cursor cursor) {
        // With the independent GPU cursor, do not repaint X11 window bitmaps
        // on pointer motion. Keep the original path as a safe fallback.
        if (cursorOverlayListener==null && _drawnCursor != null) {
            int left = _drawnCursorX - _drawnCursor.getHotspotX();
            int top = _drawnCursorY - _drawnCursor.getHotspotY();
            Bitmap bm = _drawnCursor.getBitmap();

            invalidateGuestRect(left,top,left+bm.getWidth(),top+bm.getHeight());
            _drawnCursor = null;
        }

        _currentCursor = cursor;
        _currentCursorX = x;
        _currentCursorY = y;

        Bitmap bm=cursor==null ? null : cursor.getBitmap();
        CursorOverlayListener listener=cursorOverlayListener;
        if (listener!=null)listener.onCursorUpdated(bm,x,y,
                cursor==null?0:cursor.getHotspotX(),cursor==null?0:cursor.getHotspotY());
        else if (bm!=null && !bm.isRecycled()) {
            int left=x-cursor.getHotspotX(),top=y-cursor.getHotspotY();
            invalidateGuestRect(left,top,left+bm.getWidth(),top+bm.getHeight());
        }
    }

    /**
     * Update the location of the pointer.
     *
     * @param x    New X coordinate.
     * @param y    New Y coordinate.
     * @param mode 0=Normal, 1=Grab, 2=Ungrab
     */
    private boolean pointerErrorReported;
    public void updatePointerPosition(int x, int y, int mode) {
        synchronized(_xServer) {
            if(_rootWindow==null)return;
            try { updatePointerPositionOwned(x,y,mode); }
            catch(RuntimeException error) {
                if(!pointerErrorReported) {
                    pointerErrorReported=true;
                    com.drnas.console.AndroidCrashDiagnostics.recordPointer(_xServer.getContext(),error);
                    _xServer.diagnostic("X11 pointer update FAILED: "+android.util.Log.getStackTraceString(error));
                }
                // Restore a valid origin for the next enter/motion event.
                _motionWindow=_rootWindow;
                _motionX=_currentCursorX;_motionY=_currentCursorY;
            }
        }
    }

    private void updatePointerPositionOwned(int x, int y, int mode) {
        Window w;
        Cursor c;

        if (_grabConfineWindow != null) {
            Rect rect = _grabConfineWindow.getIRect();

            if (x < rect.left) x = rect.left;
            else if (x >= rect.right) x = rect.right - 1;

            if (y < rect.top) y = rect.top;
            else if (y >= rect.bottom) y = rect.bottom - 1;
        }

        if (_grabPointerWindow != null) w = _grabPointerWindow;
        else w = _rootWindow.windowAtPoint(x, y);

        if (_grabCursor != null) c = _grabCursor;
        else c = w.getCursor();

        if (c != _currentCursor || x != _currentCursorX || y != _currentCursorY)
            movePointer(x, y, c);

        if (w != _motionWindow) {
            if(_motionWindow!=null)_motionWindow.leaveEnterNotify(x, y, w, mode);
            _motionWindow = w;
            _motionX = x;
            _motionY = y;
        } else if (x != _motionX || y != _motionY) {
            if (_grabPointerWindow == null) {
                w.motionNotify(x, y, _buttons & 0xff00, null);
            } else if (!_grabPointerSynchronous) {
                callGrabMotionNotify(w, x, y, _buttons, _grabEventMask, _grabPointerClient, _grabPointerOwnerEvents);
            } else {
                PendingPointerEvent e;
                e = new PendingGrabMotionNotify(w, x, y, _buttons, _grabEventMask, _grabPointerClient, _grabPointerOwnerEvents);
                mPendingPointerEvents.add(e);
            }

            _motionX = x;
            _motionY = y;
        }
    }

    /**
     * Update the pointer in case its glyph has changed.
     *
     * @param mode 0=Normal, 1=Grab, 2=Ungrab
     */
    public void updatePointer(int mode) {
        updatePointerPosition(_currentCursorX, _currentCursorY, mode);
    }

    /**
     * Called when a pointer button is pressed/released.
     *
     * @param button  The button that was pressed/released.
     * @param pressed True if the button was pressed.
     */
    private int buttonTraceCount;
    private int stateTraceCount;
    private volatile String inputStateSnapshot="not-observed";
    private volatile long inputStarted, inputCompleted, inputStartedAt;
    public String diagnosticInputSnapshot() {
        java.util.concurrent.ThreadPoolExecutor executor=inputExecutor;
        Thread worker=inputWorker;
        return "queuedTotal="+inputEventSerial.get()+" started="+inputStarted+
                " completed="+inputCompleted+" activeSinceMs="+inputStartedAt+
                " queue="+(executor==null?0:executor.getQueue().size())+
                " worker="+(worker==null?"none":worker.getState())+" "+inputStateSnapshot;
    }
    // Called only on the existing X11/input path; watchdog reads the immutable
    // snapshot without taking the XServer monitor or touching Window objects.
    private void traceInputState(String cause) {
        if(!nonBlockingInputEnabled)return;
        Window hit=_rootWindow==null?null:_rootWindow.windowAtPoint(_motionX,_motionY);
        inputStateSnapshot="focus="+(_focusWindow==null?0:_focusWindow.getId())+
                " hit="+(hit==null?0:hit.getId())+" xy="+_motionX+","+_motionY+
                " buttons="+_buttons+" grab="+(_grabPointerWindow==null?0:_grabPointerWindow.getId())+
                " ownerEvents="+_grabPointerOwnerEvents+" pointerSync="+_grabPointerSynchronous+
                " keyboardSync="+_grabKeyboardSynchronous+
                " pendingPointer="+mPendingPointerEvents.mQueue.size();
        if(stateTraceCount++<160)_xServer.diagnostic("DRNAS_X11_CRITICAL_INPUT_STATE cause="+
                cause+" timeMs="+android.os.SystemClock.uptimeMillis()+" "+inputStateSnapshot);
    }
    public void updatePointerButtons(int button, boolean pressed) {
        Pointer p = _xServer.getPointer();

        button = p.mapButton(button);
        if (button == 0) return;

        int mask = 0x80 << button;

        if (pressed) {
            if ((_buttons & mask) != 0) return;

            _buttons |= mask;
        } else {
            if ((_buttons & mask) == 0) return;

            _buttons &= ~mask;
        }

        traceInputState("button_"+button+(pressed?"_down":"_up"));
        if (nonBlockingInputEnabled && buttonTraceCount++ < 96)
            _xServer.diagnostic("DRNAS_X11_BUTTON button="+button+" pressed="+pressed+
                    " before="+X11ButtonState.beforeTransition(_buttons,button)+
                    " after="+_buttons+" grab="+(_grabPointerWindow==null?0:_grabPointerWindow.getId())+
                    " sync="+_grabPointerSynchronous+" pending="+mPendingPointerEvents.mQueue.size());
        if (_grabPointerWindow == null) {
            Window w = _rootWindow.windowAtPoint(_motionX, _motionY);
            PassiveButtonGrab pbg = null;

            if (pressed) pbg = w.findPassiveButtonGrab(_buttons, null);

            if (pbg != null) {
                _grabPointerClient = pbg.getGrabClient();
                _grabPointerWindow = pbg.getGrabWindow();
                _grabPointerPassive = true;
                _grabPointerAutomatic = false;
                _grabPointerTime = _xServer.getTimestamp();
                _grabConfineWindow = pbg.getConfineWindow();
                _grabEventMask = pbg.getEventMask();
                _grabPointerOwnerEvents = pbg.getOwnerEvents();
                _grabPointerSynchronous = pbg.getPointerSynchronous();
                _grabKeyboardSynchronous = pbg.getKeyboardSynchronous();

                _grabCursor = pbg.getCursor();
                if (_grabCursor == null) _grabCursor = _grabPointerWindow.getCursor();

                updatePointer(1);
                _grabPointerFreezeNextEvent = _grabPointerSynchronous;
                _grabKeyboardFreezeNextEvent = _grabKeyboardSynchronous;
                callGrabButtonNotify(_grabPointerWindow, true, _motionX, _motionY,
                        button, _grabEventMask, _grabPointerClient, _grabPointerOwnerEvents);
            } else {
                int timestamp = _xServer.getTimestamp();
                Window ew = w.buttonNotify(pressed, _motionX, _motionY, button, timestamp, null);
                if(nonBlockingInputEnabled && buttonTraceCount<=96)
                    _xServer.diagnostic("DRNAS_X11_CRITICAL_BUTTON_ROUTE pressed="+pressed+
                            " hit="+w.getId()+" deliveredWindow="+(ew==null?0:ew.getId()));
                reflectPointerFreezeNextEvent();
                Client c = null;

                if (pressed && ew != null) {
                    Vector<Client> sc;

                    sc = ew.getSelectingClients(EventCode.MaskButtonPress);
                    if (sc != null) c = sc.firstElement();
                }

                // Start an automatic key grab.
                if (c != null) {
                    int em = ew.getClientEventMask(c);

                    _grabPointerClient = c;
                    _grabPointerWindow = ew;
                    _grabPointerPassive = false;
                    _grabPointerAutomatic = true;
                    _grabPointerTime = timestamp;
                    _grabCursor = ew.getCursor();
                    _grabConfineWindow = null;
                    _grabEventMask = em & EventCode.MaskAllPointer;
                    _grabPointerOwnerEvents = (em & EventCode.MaskOwnerGrabButton) != 0;
                    _grabPointerSynchronous = false;
                    _grabKeyboardSynchronous = false;
                    updatePointer(1);
                }
            }
        } else {
            if (!_grabPointerSynchronous) {
                callGrabButtonNotify(_grabPointerWindow, pressed, _motionX, _motionY, button, _grabEventMask, _grabPointerClient, _grabPointerOwnerEvents);
            } else {
                PendingPointerEvent e;
                e = new PendingGrabButtonNotify(_grabPointerWindow, pressed, _motionX, _motionY, button, _grabEventMask, _grabPointerClient, _grabPointerOwnerEvents);
                mPendingPointerEvents.add(e);
            }

            if (_grabPointerAutomatic && !pressed && (_buttons & 0xff00) == 0) {
                _grabPointerClient = null;
                _grabPointerWindow = null;
                _grabCursor = null;
                _grabConfineWindow = null;
                updatePointer(2);
            }
        }
        traceInputState("button_done");
    }

    /**
     * Updates keycodes for modifier keys (i.e. shift/alt).
     */
    private void updateModifiers() {
        int mask = 0;

		Keyboard kb = _xServer.getKeyboard();
		mask = kb.getState();

        _buttons = (_buttons & 0xff00) | mask;
    }

    /**
     * Called when a key is pressed or released.
     *
     * @param keycode Keycode of the key.
     * @param pressed True if pressed, false if released.
     */
    public void notifyKeyPressedReleased(int keycode, boolean pressed) {
        if (_grabKeyboardWindow == null && _focusWindow == null) return;

        Keyboard kb = _xServer.getKeyboard();

        keycode = kb.translateToXKeycode(keycode);

        if (pressed && _grabKeyboardWindow == null) {
            PassiveKeyGrab pkg = _focusWindow.findPassiveKeyGrab(keycode, _buttons & 0xff, null);

            if (pkg == null) {
                Window w = _rootWindow.windowAtPoint(_motionX, _motionY);

                if (w.isAncestor(_focusWindow))
                    pkg = w.findPassiveKeyGrab(keycode, _buttons & 0xff, null);
            }

            if (pkg != null) {
                _grabKeyboardPassiveGrab = pkg;
                _grabKeyboardClient = pkg.getGrabClient();
                _grabKeyboardWindow = pkg.getGrabWindow();
                _grabKeyboardTime = _xServer.getTimestamp();
                _grabKeyboardOwnerEvents = pkg.getOwnerEvents();
                _grabPointerSynchronous = pkg.getPointerSynchronous();
                _grabKeyboardSynchronous = pkg.getKeyboardSynchronous();
            }
        }

        if (_grabKeyboardWindow == null) {
            Window w = _rootWindow.windowAtPoint(_motionX, _motionY);

            if (w.isAncestor(_focusWindow)) w.keyNotify(pressed, _motionX, _motionY, keycode, null);
            else _focusWindow.keyNotify(pressed, _motionX, _motionY, keycode, null);
            reflectKeyboardFreezeNextEvent();
        } else if (!_grabKeyboardSynchronous) {
            callGrabKeyNotify(_grabKeyboardWindow, pressed, _motionX, _motionY, keycode, _grabKeyboardClient, _grabKeyboardOwnerEvents);
        } else {
            PendingKeyboardEvent e;
            e = new PendingGrabKeyNotify(_grabKeyboardWindow, pressed, _motionX, _motionY, keycode, _grabKeyboardClient, _grabKeyboardOwnerEvents);
            mPendingKeyboardEvents.add(e);
        }

        kb.updateKeymap(keycode, pressed);

        if (!pressed && _grabKeyboardPassiveGrab != null) {
            int rk = _grabKeyboardPassiveGrab.getKey();

            if (rk == 0 || rk == keycode) {
                _grabKeyboardPassiveGrab = null;
                _grabKeyboardClient = null;
                _grabKeyboardWindow = null;
            }
        }
    }

    /**
     * Called when there is a key down event.
     *
     * @param keycode The value in event.getKeyCode().
     * @param event   The key event.
     * @return True if the event was handled.
     */
    @Override
    public boolean onKeyDown(int keycode, KeyEvent event) {
        if(nonBlockingInputEnabled && onAndroidUiThread()) {
            final KeyEvent owned=new KeyEvent(event);
            final int originalKeycode=keycode;
            queueInput("key_"+originalKeycode+"_down",()->onKeyDown(originalKeycode,owned));
            return true;
        }
        synchronized (_xServer) {
            if (_rootWindow == null) return false;

            blank(false);    // Reset the screen saver.

            boolean sendEvent = false;

			if (_arrowsAsButtons) {
				switch (keycode) {
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_CENTER:
					updatePointerButtons (1, true);
					return true;
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
					updatePointerButtons (2, true);
					return true;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					updatePointerButtons (3, true);
					return true;
				}
			}

			switch (keycode) {
				case KeyEvent.KEYCODE_BACK:
					if (! _inhibitBackButton)
						return false;
					keycode = 128 - _xServer.getKeyboard().getMinimumKeycodeDiff(); // Special keycode since keycode value 5 is out of range
					sendEvent = true;
					break;
				case KeyEvent.KEYCODE_MENU:
					return false;
				case KeyEvent.KEYCODE_VOLUME_UP:
					updatePointerButtons (1, true);
					break;
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					updatePointerButtons (3, true);
					break;
				default:
					sendEvent = true;
					break;
			}
	
			updateModifiers();
            if (sendEvent) notifyKeyPressedReleased(keycode, true);
        }

        return true;
    }

    /**
     * Called when there is a key up event.
     *
     * @param keycode The value in event.getKeyCode().
     * @param event   The key event.
     * @return True if the event was handled.
     */
    @Override
    public boolean onKeyUp(int keycode, KeyEvent event) {
        if(nonBlockingInputEnabled && onAndroidUiThread()) {
            final KeyEvent owned=new KeyEvent(event);
            final int originalKeycode=keycode;
            queueInput("key_"+originalKeycode+"_up",()->onKeyUp(originalKeycode,owned));
            return true;
        }
        synchronized (_xServer) {
            if (_rootWindow == null) return false;

            blank(false);    // Reset the screen saver.

            boolean sendEvent = false;

			if (_arrowsAsButtons) {
				switch (keycode) {
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_CENTER:
					updatePointerButtons (1, false);
					return true;
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
					updatePointerButtons (2, false);
					return true;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					updatePointerButtons (3, false);
					return true;
				}
			}

			switch (keycode) {
				case KeyEvent.KEYCODE_BACK:
					if (! _inhibitBackButton)
						return false;
					keycode = 128 - _xServer.getKeyboard().getMinimumKeycodeDiff(); // Special keycode since keycode value 5 is out of range
					sendEvent = true;
					break;
				case KeyEvent.KEYCODE_MENU:
					return false;
				case KeyEvent.KEYCODE_VOLUME_UP:
					updatePointerButtons (1, false);
					break;
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					updatePointerButtons (3, false);
					break;
				default:
					sendEvent = true;
					break;
			}
	
			updateModifiers();
            if (sendEvent) notifyKeyPressedReleased(keycode, false);
        }

        return true;
    }

    /**
     * Write details of the screen.
     *
     * @param io The input/output stream.
     * @throws IOException
     */
    public void write(InputOutput io) throws IOException {
        Visual vis = _xServer.getRootVisual();

        io.writeInt(_rootWindow.getId());        // Root window ID.
        io.writeInt(_defaultColormap.getId());    // Default colormap ID.
        io.writeInt(_defaultColormap.getWhitePixel());    // White pixel.
        io.writeInt(_defaultColormap.getBlackPixel());    // Black pixel.
        io.writeInt(0);    // Current input masks.
        io.writeShort((short) getGuestWidth());    // Width in pixels.
        io.writeShort((short) getGuestHeight());    // Height in pixels.
        io.writeShort((short) (getGuestWidth() / _pixelsPerMillimeter));    // Width in millimeters.
        io.writeShort((short) (getGuestHeight() / _pixelsPerMillimeter));    // Height in millimeters.
        io.writeShort((short) 1);    // Minimum installed maps.
        io.writeShort((short) 1);    // Maximum installed maps.
        io.writeInt(vis.getId());    // Root visual ID.
        io.writeByte(vis.getBackingStoreInfo());
        io.writeByte((byte) (vis.getSaveUnder() ? 1 : 0));
        io.writeByte((byte) vis.getDepth());    // Root depth.
        io.writeByte((byte) 1);    // Number of allowed depths.

        // Write the only allowed depth.
        io.writeByte((byte) vis.getDepth());    // Depth.
        io.writeByte((byte) 0);    // Unused.
        io.writeShort((short) 1);    // Number of visuals with this depth.
        io.writePadBytes(4);    // Unused.
        vis.write(io);        // The visual at this depth.
    }

    /**
     * Write the screen's installed colormaps.
     *
     * @param client The remote client.
     * @throws IOException
     */
    public void writeInstalledColormaps(Client client) throws IOException {
        InputOutput io = client.getInputOutput();
        int n = _installedColormaps.size();

        synchronized (io) {
            Util.writeReplyHeader(client, (byte) 0);
            io.writeInt(n);    // Reply length.
            io.writeShort((short) n);    // Number of colormaps.
            io.writePadBytes(22);    // Unused.

            for (Colormap cmap : _installedColormaps)
                io.writeInt(cmap.getId());
        }
        io.flush();
    }

    /**
     * Process a screen-related request.
     *
     * @param xServer        The X server.
     * @param client         The remote client.
     * @param opcode         The request's opcode.
     * @param arg            Optional first argument.
     * @param bytesRemaining Bytes yet to be read in the request.
     * @throws IOException
     */
    public void processRequest(XServer xServer, Client client, byte opcode, byte arg, int bytesRemaining) throws IOException {
        InputOutput io = client.getInputOutput();
        if((opcode & 255)>=26 && (opcode & 255)<=35 || opcode==RequestCode.SetInputFocus)
            traceInputState("request_"+(opcode & 255)+"_arg_"+(arg & 255)+"_client_"+client.resourceIdBase());

        switch (opcode) {
            case RequestCode.SendEvent:
                if (bytesRemaining != 40) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    processSendEventRequest(_xServer, client, arg == 1);
                }
                break;
            case RequestCode.GrabPointer:
                if (bytesRemaining != 20) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    processGrabPointerRequest(_xServer, client, arg == 1);
                }
                break;
            case RequestCode.UngrabPointer:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int time = io.readInt();    // Time.
                    int now = _xServer.getTimestamp();

                    if (time == 0) time = now;

                    if (time >= _grabPointerTime && time <= now && _grabPointerClient == client) {
                        _grabPointerClient = null;
                        _grabPointerWindow = null;
                        _grabCursor = null;
                        _grabConfineWindow = null;
                        updatePointer(2);
                    }
                }
                break;
            case RequestCode.GrabButton:
                if (bytesRemaining != 20) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    processGrabButtonRequest(_xServer, client, arg == 1);
                }
                break;
            case RequestCode.UngrabButton:
                if (bytesRemaining != 8) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int wid = io.readInt();    // Grab window.
                    int modifiers = io.readShort();    // Modifiers.
                    Resource r = _xServer.getResource(wid);

                    io.readSkip(2);    // Unused.

                    if (r == null || r.getType() != Resource.WINDOW) {
                        ErrorCode.write(client, ErrorCode.Window, opcode, wid);
                    } else {
                        Window w = (Window) r;

                        w.removePassiveButtonGrab(arg, modifiers);
                    }
                }
                break;
            case RequestCode.ChangeActivePointerGrab:
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int cid = io.readInt();    // Cursor.
                    int time = io.readInt();    // Time.
                    int mask = io.readShort();    // Event mask.
                    Cursor c = null;

                    io.readSkip(2);    // Unused.

                    if (cid != 0) {
                        Resource r = _xServer.getResource(cid);

                        if (r == null || r.getType() != Resource.CURSOR)
                            ErrorCode.write(client, ErrorCode.Cursor, opcode, 0);
                        else c = (Cursor) r;
                    }

                    int now = _xServer.getTimestamp();

                    if (time == 0) time = now;

                    if (_grabPointerWindow != null && !_grabPointerPassive && _grabPointerClient == client && time >= _grabPointerTime && time <= now && (cid == 0 || c != null)) {
                        _grabEventMask = mask;
                        if (c != null) _grabCursor = c;
                        else _grabCursor = _grabPointerWindow.getCursor();
                    }
                }
                break;
            case RequestCode.GrabKeyboard:
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    processGrabKeyboardRequest(_xServer, client, arg == 1);
                }
                break;
            case RequestCode.UngrabKeyboard:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int time = io.readInt();    // Time.
                    int now = _xServer.getTimestamp();

                    if (time == 0) time = now;

                    if (time >= _grabKeyboardTime && time <= now) {
                        Window pw = _rootWindow.windowAtPoint(_motionX, _motionY);

                        Window.focusInOutNotify(_grabKeyboardWindow, _focusWindow, pw, _rootWindow, 2);
                        _grabKeyboardClient = null;
                        _grabKeyboardWindow = null;
                    }
                }
                break;
            case RequestCode.GrabKey:
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    processGrabKeyRequest(_xServer, client, arg == 1);
                }
                break;
            case RequestCode.UngrabKey:
                if (bytesRemaining != 8) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int wid = io.readInt();    // Grab window.
                    int modifiers = io.readShort();    // Modifiers.
                    Resource r = _xServer.getResource(wid);

                    io.readSkip(2);    // Unused.

                    if (r == null || r.getType() != Resource.WINDOW) {
                        ErrorCode.write(client, ErrorCode.Window, opcode, wid);
                    } else {
                        Window w = (Window) r;

                        w.removePassiveKeyGrab(arg, modifiers);
                    }
                }
                break;
            case RequestCode.AllowEvents:
                processAllowEvents(client, opcode, io, bytesRemaining, arg);
                break;
            case RequestCode.SetInputFocus:
                if (bytesRemaining != 8) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    processSetInputFocusRequest(_xServer, client, arg);
                }
                break;
            case RequestCode.GetInputFocus:
                if (bytesRemaining != 0) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int wid;

                    if (_focusWindow == null) wid = 0;
                    else if (_focusWindow == _rootWindow) wid = 1;
                    else wid = _focusWindow.getId();

                    synchronized (io) {
                        Util.writeReplyHeader(client, _focusRevertTo);
                        io.writeInt(0);    // Reply length.
                        io.writeInt(wid);    // Focus window.
                        io.writePadBytes(20);    // Unused.
                    }
                    io.flush();
                }
                break;
        }
        if(((opcode & 255)>=26 && (opcode & 255)<=35) || opcode==RequestCode.SetInputFocus)
            traceInputState("request_done_"+(opcode & 255));
    }

    private void processAllowEvents(Client client, byte opcode, InputOutput io, int bytesRemaining, byte mode) throws IOException {
        if (bytesRemaining != 4) {
            io.readSkip(bytesRemaining);
            ErrorCode.write(client, ErrorCode.Length, opcode, 0);
            return;
        }

        int t = io.readInt();
        int now = _xServer.getTimestamp();
        int time = t == 0 ? now : t;
        if ((now < time) || (time < _grabPointerTime) || (time < _grabKeyboardTime)) {
            return;
        }

        String message;
        switch (mode) {
            case RequestCode.AllowEventsMode.AsyncPointer:
                flushPendingPointerEvents();
                _grabPointerSynchronous = false;
                _grabPointerFreezeNextEvent = false;
                break;
            case RequestCode.AllowEventsMode.SyncPointer:
                flushPendingPointerEvents();
                _grabPointerSynchronous = false;
                _grabPointerFreezeNextEvent = true;
                break;
            case RequestCode.AllowEventsMode.AsyncKeyboard:
                flushPendingKeyboardEvents();
                _grabKeyboardSynchronous = false;
                _grabKeyboardFreezeNextEvent = false;
                break;
            case RequestCode.AllowEventsMode.SyncKeyboard:
                flushPendingKeyboardEvents();
                _grabKeyboardSynchronous = false;
                _grabKeyboardFreezeNextEvent = true;
                break;
            case RequestCode.AllowEventsMode.AsyncBoth:
            case RequestCode.AllowEventsMode.SyncBoth:
            case RequestCode.AllowEventsMode.ReplayPointer:
            case RequestCode.AllowEventsMode.ReplayKeyboard:
                String fmt = "unsupported AllowEvents mode: %d (%s)";
                String name = RequestCode.AllowEventsMode.toString(mode);
                message = String.format(fmt, mode, name);
                reportError(client, ErrorCode.Implementation, opcode, message);
                break;
            default:
                message = String.format("unknown AllowEvents mode: %d", mode);
                reportError(client, ErrorCode.Value, opcode, message);
                break;
        }
    }

    private void reportError(Client client, byte error, byte opcode, String message) throws IOException {
        ErrorCode.write(client, error, opcode, 0);
    }

    /**
     * Toggle Arrows As Buttons.
     *
     * Switch between key and button events for arrow keys
     *
     * @return new state of switch
     */
    public boolean toggleArrowsAsButtons() {
        _arrowsAsButtons = !_arrowsAsButtons;
        return _arrowsAsButtons;
    }

    /**
     * Toggle shared clipboard. Shared clipboard works when using the long press 
     * action shortcuts.
     *
     * @return new state of switch
     */
    public boolean toggleSharedClipboard() {
        _sharedClipboard = !_sharedClipboard;
        return _sharedClipboard;
    }

    /**
     * Toggle Inhibit Back Button.
     *
     * @return new state of switch
     */
    public boolean toggleInhibitBackButton() {
        _inhibitBackButton = !_inhibitBackButton;
        return _inhibitBackButton;
    }

    /**
     * Toggle touchscreen mouse click emulation.
     *
     * @return new state of switch
     */
    public boolean toggleEnableTouchClicks() {
        _enableTouchClicks = !_enableTouchClicks;
        return _enableTouchClicks;
    }

    /**
     * Process a SendEvent request.
     *
     * @param xServer   The X server.
     * @param client    The remote client.
     * @param propagate Propagate flag.
     * @throws IOException
     */
    private void processSendEventRequest(XServer xServer, Client client, boolean propagate) throws IOException {
        InputOutput io = client.getInputOutput();
        int wid = io.readInt();    // Destination window.
        int mask = io.readInt();    // Event mask.
        byte[] event = new byte[32];
        Window w;

        io.readBytes(event, 0, 32);    // Event.

        if (wid == 0) {        // Pointer window.
            w = _rootWindow.windowAtPoint(_motionX, _motionY);
        } else if (wid == 1) {    // Input focus.
            if (_focusWindow == null) {
                ErrorCode.write(client, ErrorCode.Window, RequestCode.SendEvent, wid);
                return;
            }

            Window pw = _rootWindow.windowAtPoint(_motionX, _motionY);

            if (pw.isAncestor(_focusWindow)) w = pw;
            else w = _focusWindow;
        } else {
            Resource r = _xServer.getResource(wid);

            if (r == null || r.getType() != Resource.WINDOW) {
                ErrorCode.write(client, ErrorCode.Window, RequestCode.SendEvent, wid);
                return;
            } else w = (Window) r;
        }

        Vector<Client> dc = null;

        if (mask == 0) {
            dc = new Vector<Client>();
            dc.add(w.getClient());
        } else if (!propagate) {
            dc = w.getSelectingClients(mask);
        } else {
            for (; ; ) {
                if ((dc = w.getSelectingClients(mask)) != null) break;

                mask &= ~w.getDoNotPropagateMask();
                if (mask == 0) break;

                w = w.getParent();
                if (w == null) break;
                if (wid == 1 && w == _focusWindow) break;
            }
        }

        if (dc == null) return;

        for (Client c : dc) {
            if (c == null) continue;
            InputOutput dio = c.getInputOutput();

            synchronized (dio) {
                dio.writeByte((byte) (event[0] | 128));

                if (event[0] == EventCode.KeymapNotify) {
                    dio.writeBytes(event, 1, 31);
                } else {
                    dio.writeByte(event[1]);
                    dio.writeShort((short) (c.getSequenceNumber() & 0xffff));
                    dio.writeBytes(event, 4, 28);
                }
            }
            dio.flush();
        }
    }

    /**
     * Process a GrabPointer request.
     *
     * @param xServer     The X server.
     * @param client      The remote client.
     * @param ownerEvents Owner-events flag.
     * @throws IOException
     */
    private void processGrabPointerRequest(XServer xServer, Client client, boolean ownerEvents) throws IOException {
        InputOutput io = client.getInputOutput();
        int wid = io.readInt();    // Grab window.
        int mask = io.readShort();    // Event mask.
        boolean psync = (io.readByte() == 0);    // Pointer mode.
        boolean ksync = (io.readByte() == 0);    // Keyboard mode.
        int cwid = io.readInt();    // Confine-to.
        int cid = io.readInt();    // Cursor.
        int time = io.readInt();    // Time.
        Resource r = _xServer.getResource(wid);

        if (r == null || r.getType() != Resource.WINDOW) {
            ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabPointer, wid);
            return;
        }

        Window w = (Window) r;
        Cursor c = null;
        Window cw = null;

        if (cwid != 0) {
            r = _xServer.getResource(cwid);

            if (r == null || r.getType() != Resource.WINDOW) {
                ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabPointer, cwid);
                return;
            }
            cw = (Window) r;
        }

        if (cid != 0) {
            r = _xServer.getResource(cid);
            if (r != null && r.getType() != Resource.CURSOR) {
                ErrorCode.write(client, ErrorCode.Cursor, RequestCode.GrabPointer, cid);
                return;
            }

            c = (Cursor) r;
        }

        if (c == null) c = w.getCursor();

        byte status = 0;    // Success.
        int now = _xServer.getTimestamp();

        if (time == 0) time = now;

        if (time < _grabPointerTime || time > now) {
            status = 2;    // Invalid time.
        } else if (_grabPointerWindow != null && _grabPointerClient != client) {
            status = 1;    // Already grabbed.
        } else {
            _grabPointerClient = client;
            _grabPointerWindow = w;
            _grabPointerPassive = false;
            _grabPointerAutomatic = false;
            _grabPointerTime = time;
            _grabCursor = c;
            _grabConfineWindow = cw;
            _grabEventMask = mask;
            _grabPointerOwnerEvents = ownerEvents;
            _grabPointerSynchronous = psync;
            _grabKeyboardSynchronous = ksync;
        }

        synchronized (io) {
            Util.writeReplyHeader(client, status);
            io.writeInt(0);    // Reply length.
            io.writePadBytes(24);    // Unused.
        }
        io.flush();

        if (status == 0) updatePointer(1);
    }

    /**
     * Process a GrabButton request.
     *
     * @param xServer     The X server.
     * @param client      The remote client.
     * @param ownerEvents Owner-events flag.
     * @throws IOException
     */
    private void processGrabButtonRequest(XServer xServer, Client client, boolean ownerEvents) throws IOException {
        InputOutput io = client.getInputOutput();
        int wid = io.readInt();    // Grab window.
        int mask = io.readShort();    // Event mask.
        boolean psync = (io.readByte() == 0);    // Pointer mode.
        boolean ksync = (io.readByte() == 0);    // Keyboard mode.
        int cwid = io.readInt();    // Confine-to.
        int cid = io.readInt();    // Cursor.
        byte button = (byte) io.readByte();    // Button.
        int modifiers;
        Resource r = _xServer.getResource(wid);

        io.readSkip(1);    // Unused.
        modifiers = io.readShort();    // Modifiers.

        if (r == null || r.getType() != Resource.WINDOW) {
            ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabPointer, wid);
            return;
        }

        Window w = (Window) r;
        Cursor c = null;
        Window cw = null;

        if (cwid != 0) {
            r = _xServer.getResource(cwid);

            if (r == null || r.getType() != Resource.WINDOW) {
                ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabPointer, cwid);
                return;
            }
            cw = (Window) r;
        }

        if (cid != 0) {
            r = _xServer.getResource(cid);

            if (r != null && r.getType() != Resource.CURSOR) {
                ErrorCode.write(client, ErrorCode.Cursor, RequestCode.GrabPointer, cid);
                return;
            }
            c = (Cursor) r;
        }

        w.addPassiveButtonGrab(new PassiveButtonGrab(client, w, button, modifiers, ownerEvents, mask, psync, ksync, cw, c));
    }

    /**
     * Process a GrabKeyboard request.
     *
     * @param xServer     The X server.
     * @param client      The remote client.
     * @param ownerEvents Owner-events flag.
     * @throws IOException
     */
    private void processGrabKeyboardRequest(XServer xServer, Client client, boolean ownerEvents) throws IOException {
        InputOutput io = client.getInputOutput();
        int wid = io.readInt();    // Grab window.
        int time = io.readInt();    // Time.
        boolean psync = (io.readByte() == 0);    // Pointer mode.
        boolean ksync = (io.readByte() == 0);    // Keyboard mode.
        Resource r = _xServer.getResource(wid);

        io.readSkip(2);    // Unused.

        if (r == null || r.getType() != Resource.WINDOW) {
            ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabKeyboard, wid);
            return;
        }

        Window w = (Window) r;
        byte status = 0;    // Success.
        int now = _xServer.getTimestamp();

        if (time == 0) time = now;

        if (time < _grabKeyboardTime || time > now) {
            status = 2;    // Invalid time.
        } else if ((_grabKeyboardWindow != null) && (_grabKeyboardClient != client)) {
            status = 1;    // Already grabbed.
        } else {
            _grabKeyboardClient = client;
            _grabKeyboardWindow = w;
            _grabKeyboardTime = time;
            _grabKeyboardOwnerEvents = ownerEvents;
            _grabPointerSynchronous = psync;
            _grabKeyboardSynchronous = ksync;
        }

        synchronized (io) {
            Util.writeReplyHeader(client, status);
            io.writeInt(0);    // Reply length.
            io.writePadBytes(24);    // Unused.
        }
        io.flush();

        if (status == 0)
            Window.focusInOutNotify(_focusWindow, w, _rootWindow.windowAtPoint(_motionX, _motionY), _rootWindow, 1);
    }

    /**
     * Process a GrabKey request.
     *
     * @param xServer     The X server.
     * @param client      The remote client.
     * @param ownerEvents Owner-events flag.
     * @throws IOException
     */
    private void processGrabKeyRequest(XServer xServer, Client client, boolean ownerEvents) throws IOException {
        InputOutput io = client.getInputOutput();
        int wid = io.readInt();    // Grab window.
        int modifiers = io.readShort();    // Modifiers.
        byte keycode = (byte) io.readByte();    // Key.
        boolean psync = (io.readByte() == 0);    // Pointer mode.
        boolean ksync = (io.readByte() == 0);    // Keyboard mode.
        Resource r = _xServer.getResource(wid);

        io.readSkip(3);    // Unused.

        if (r == null || r.getType() != Resource.WINDOW) {
            ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabPointer, wid);
            return;
        }

        Window w = (Window) r;

        w.addPassiveKeyGrab(new PassiveKeyGrab(client, w, keycode, modifiers, ownerEvents, psync, ksync));
    }

    /**
     * Process a SetInputFocus request.
     *
     * @param xServer  The X server.
     * @param client   The remote client.
     * @param revertTo 0=None, 1=Root, 2=Parent.
     * @throws IOException
     */
    private void processSetInputFocusRequest(XServer xServer, Client client, byte revertTo) throws IOException {
        InputOutput io = client.getInputOutput();
        int wid = io.readInt();    // Focus window.
        int time = io.readInt();    // Time.
        traceInputState("focus_request_target_"+wid+"_client_"+client.resourceIdBase()+"_time_"+time);
        Window w;

        if (wid == 0) {
            w = null;
            revertTo = 0;
        } else if (wid == 1) {
            w = _rootWindow;
            revertTo = 0;
        } else {
            Resource r = xServer.getResource(wid);

            if (r == null || r.getType() != Resource.WINDOW) {
                ErrorCode.write(client, ErrorCode.Window, RequestCode.GrabPointer, wid);
                return;
            }

            w = (Window) r;
        }

        int now = xServer.getTimestamp();

        if (time == 0) time = now;

        if (time < _focusLastChangeTime || time > now) return;

        Window.focusInOutNotify(_focusWindow, w, _rootWindow.windowAtPoint(_motionX, _motionY), _rootWindow, _grabKeyboardWindow == null ? 0 : 3);

        _focusWindow = w;
        _focusRevertTo = revertTo;
        _focusLastChangeTime = time;
        traceInputState("focus_applied");
    }

    private void reflectPointerFreezeNextEvent() {
        _grabPointerSynchronous = _grabPointerFreezeNextEvent;
    }

    private void reflectKeyboardFreezeNextEvent() {
        _grabKeyboardSynchronous = _grabKeyboardFreezeNextEvent;
    }

    private void callGrabButtonNotify(Window w, boolean pressed, int motionX, int motionY, int button, int grabEventMask, Client grabPointerClient, boolean grabPointerOwnerEvents) {
        callGrabButtonNotify(w, pressed, motionX, motionY, button, grabEventMask,
                grabPointerClient, grabPointerOwnerEvents, X11ButtonState.beforeTransition(_buttons,button));
    }

    private void callGrabButtonNotify(Window w, boolean pressed, int motionX, int motionY, int button,
            int grabEventMask, Client grabPointerClient, boolean grabPointerOwnerEvents, int eventState) {
        traceInputState("grab_delivery_window_"+w.getId()+"_pressed_"+pressed+"_mask_"+grabEventMask);
        w.grabButtonNotify(pressed, motionX, motionY, button, grabEventMask,
                grabPointerClient, grabPointerOwnerEvents, eventState);
        reflectPointerFreezeNextEvent();
    }

    private void callGrabMotionNotify(Window w, int x, int y, int buttons, int grabEventMask, Client grabPointerClient, boolean grabPointerOwnerEvents) {
        w.grabMotionNotify(x, y, buttons & 0xff00, grabEventMask, grabPointerClient, grabPointerOwnerEvents);
    }

    private void callGrabKeyNotify(Window w, boolean pressed, int motionX, int motionY, int keycode, Client grabKeyboardClient, boolean grabKeyboardOwnerEvents) {
        w.grabKeyNotify(pressed, motionX, motionY, keycode, grabKeyboardClient, grabKeyboardOwnerEvents);
        reflectKeyboardFreezeNextEvent();
    }

    private void flushPendingPointerEvents() {
        flushPendingEvents(mPendingPointerEvents);
    }

    private void flushPendingKeyboardEvents() {
        flushPendingEvents(mPendingKeyboardEvents);
    }

    private void flushPendingEvents(PendingEventQueue q) {
        PendingEvent e;
        while ((e = q.next()) != null) {
            e.run();
        }
    }

    /**
     * @return true if shared clipboard is enabled, false otherwise.
     */
    public boolean hasSharedClipboard() {
        return _sharedClipboard;
    }

    /**
     * @return Window used for shared clipboard.
     */
    public Window getSharedClipboardWindow(){
        return _sharedClipboardWindow;
    }
}




