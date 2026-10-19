package com.drnas.console;

/** One balanced mouse press per pointer gesture, including cancellation. */
final class MouseButtonHold {
    interface Sink {void send(int button,boolean pressed);}
    private final Sink sink;
    private int held;
    MouseButtonHold(Sink sink){this.sink=sink;}
    boolean isHeld(int button){return (held & (1<<button))!=0;}
    void press(int button){
        if(isHeld(button))return;
        held|=1<<button;sink.send(button,true);
    }
    void release(int button){
        if(!isHeld(button))return;
        held&=~(1<<button);sink.send(button,false);
    }
    void releaseAll(){for(int button=1;button<=3;button++)release(button);}
}
