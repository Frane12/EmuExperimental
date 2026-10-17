package com.drnas.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;

/**
 * The exact landscape from our approved EmuDrnas concept, cropped to the
 * background only (no mock-up controls). All existing dashboard controls,
 * positions and Wine runtime behavior remain untouched.
 *
 * Decode once: no allocations, randomness, bitmaps or I/O in onDraw.
 */
public final class MeadowBannerView extends View {
    private static final String ASSET = "emudrnas-meadow-r6.webp";
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap meadow;

    public MeadowBannerView(Context context) {
        super(context);
        Bitmap decoded = null;
        try (InputStream input = context.getAssets().open(ASSET)) {
            decoded = BitmapFactory.decodeStream(input);
            if (decoded == null) {
                Log.e("EmuDrnas-Meadow", "The bundled meadow could not be decoded");
            }
        } catch (IOException exception) {
            Log.e("EmuDrnas-Meadow", "Bundled meadow missing", exception);
        }
        meadow = decoded;
        setContentDescription("Retro Windows livada, raskošno stablo, oblaci i cvijeće");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth(), height = getHeight();
        if (width < 2 || height < 2) return;

        final float radius = Math.min(width, height) * 0.11f;
        final RectF destination = new RectF(0, 0, width, height);
        final Path rounded = new Path();
        rounded.addRoundRect(destination, radius, radius, Path.Direction.CW);

        int saved = canvas.save();
        canvas.clipPath(rounded);
        if (meadow != null && !meadow.isRecycled()) {
            // FitXY intentionally preserves the entire concept artwork: tree
            // on the left, flowers, trail and sun on the right are all visible.
            canvas.drawBitmap(meadow,
                new Rect(0, 0, meadow.getWidth(), meadow.getHeight()),
                destination, bitmapPaint);
        } else {
            fallbackPaint.setShader(new LinearGradient(0, 0, 0, height,
                    0xff087cde, 0xff56cd55, Shader.TileMode.CLAMP));
            canvas.drawRect(destination, fallbackPaint);
            fallbackPaint.setShader(null);
        }
        canvas.restoreToCount(saved);

        // Thin neon blue / mint glow; no heavy software rendering layer.
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(2f, getResources()
                .getDisplayMetrics().density * 2.1f));
        borderPaint.setColor(0x6627F8F2);
        canvas.drawRoundRect(new RectF(1, 1, width-1, height-1), radius, radius, borderPaint);
        borderPaint.setStrokeWidth(Math.max(1f, getResources()
                .getDisplayMetrics().density * 0.9f));
        borderPaint.setColor(0xff4ae9f5);
        canvas.drawRoundRect(new RectF(2, 2, width-2, height-2), radius, radius, borderPaint);
        borderPaint.setStyle(Paint.Style.FILL);
    }

    @Override protected void onDetachedFromWindow() {
        // The Bitmap is owned by this View; a new Activity decodes its own.
        super.onDetachedFromWindow();
    }
}
