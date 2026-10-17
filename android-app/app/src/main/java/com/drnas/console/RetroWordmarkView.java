package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.widget.TextView;

/** Resolution-independent retro wordmark, sized by the existing header. */
final class RetroWordmarkView extends TextView {
    private final Paint accent=new Paint(Paint.ANTI_ALIAS_FLAG);
    RetroWordmarkView(Context context){
        super(context);
        setText("EmuDrnas");setContentDescription("EmuDrnas");
        setAutoSizeTextTypeUniformWithConfiguration(14,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        setTextSize(20);setTypeface(Typeface.create("monospace",Typeface.BOLD));
        setSingleLine(true);setEllipsize(TextUtils.TruncateAt.END);
        setIncludeFontPadding(false);setLetterSpacing(.015f);
        setTextColor(0xffc8fff0);
        float d=getResources().getDisplayMetrics().density;
        setPadding(0,0,0,Math.round(3*d));
        setShadowLayer(2.5f*d,0,0,0x8861f7c3);
        accent.setColor(0xff65e6c4);accent.setStrokeWidth(d);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        getPaint().setShader(new LinearGradient(0,0,Math.max(1,w),h,
                new int[]{0xffeffff8,0xff86f6ca,0xff61ddeb},null,Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density;
        float width=Math.min(getWidth(),getPaint().measureText("EmuDrnas"));
        float y=getHeight()-d;
        accent.setAlpha(135);canvas.drawLine(0,y,width*.75f,y,accent);
        accent.setAlpha(70);canvas.drawLine(width*.80f,y,width*.91f,y,accent);
        accent.setAlpha(45);canvas.drawLine(width*.95f,y,width,y,accent);
    }
}
