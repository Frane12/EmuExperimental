/* SPDX-FileCopyrightText: 2026 DRNAS contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Source overlay for the native Android ARM64 Blender backend.
 * Uses the upstream BlenderActivity.nativeOnKey() -> GHOST event queue.
 */
package org.blender.blender;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** A compact, opt-in toolbar, not an overlay capturing the Blender surface. */
public final class DrnasNativeControls {
  @FunctionalInterface
  public interface KeySink {
    void dispatch(int keyCode, int action, int metaState);
  }

  private final Activity host;
  private final KeySink sink;
  private final List<KeyControl> keys = new ArrayList<>();
  private LinearLayout actionRail;
  private boolean expanded;

  public DrnasNativeControls(Activity host, KeySink sink) {
    this.host = host;
    this.sink = sink;
  }

  public void install() {
    LinearLayout container = new LinearLayout(host);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setGravity(Gravity.END);
    // Empty viewport is NOT covered by a full-screen intercepting overlay.
    Button toggle = makeButton("DRNAS");
    container.addView(toggle);

    actionRail = new LinearLayout(host);
    actionRail.setOrientation(LinearLayout.VERTICAL);
    addKey(actionRail, "TAB", KeyEvent.KEYCODE_TAB);
    addKey(actionRail, "ESC", KeyEvent.KEYCODE_ESCAPE);
    addKey(actionRail, "Move", KeyEvent.KEYCODE_G);
    addKey(actionRail, "Rotate", KeyEvent.KEYCODE_R);
    addKey(actionRail, "Scale", KeyEvent.KEYCODE_S);
    addKey(actionRail, "Delete", KeyEvent.KEYCODE_X);
    actionRail.setVisibility(View.GONE);
    container.addView(actionRail);

    toggle.setOnClickListener(v -> {
      if (expanded) {
        releaseAll();
      }
      expanded = !expanded;
      actionRail.setVisibility(expanded ? View.VISIBLE : View.GONE);
    });

    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.TOP | Gravity.END);
    lp.topMargin = dp(12);
    lp.rightMargin = dp(12);
    host.addContentView(container, lp);
  }

  /** Guarantee matching UP on cancel, focus loss and toolbar collapse. */
  public void releaseAll() {
    for (KeyControl key : keys) {
      key.release();
    }
  }

  private void addKey(LinearLayout parent, String title, int keyCode) {
    KeyControl control = new KeyControl(keyCode);
    keys.add(control);
    Button button = makeButton(title);
    button.setOnTouchListener((v, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          control.press();
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          control.release();
          return true;
        default:
          // Events owned by this key stay owned until UP/CANCEL.
          return true;
      }
    });
    parent.addView(button);
  }

  private Button makeButton(String label) {
    Button button = new Button(host);
    button.setText(label);
    button.setTextSize(11);
    button.setAllCaps(false);
    button.setTextColor(Color.WHITE);
    button.setFocusable(false);
    button.setFocusableInTouchMode(false);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    GradientDrawable background = new GradientDrawable();
    background.setColor(Color.argb(205, 23, 30, 44));
    background.setCornerRadius(dp(8));
    background.setStroke(dp(1), Color.argb(220, 88, 151, 180));
    button.setBackground(background);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(66), dp(43));
    lp.bottomMargin = dp(3);
    button.setLayoutParams(lp);
    return button;
  }

  private int dp(int dip) {
    return (int) (dip * host.getResources().getDisplayMetrics().density + 0.5f);
  }

  private final class KeyControl {
    final int code;
    boolean held;

    KeyControl(int code) {
      this.code = code;
    }

    void press() {
      if (!held) {
        held = true;
        sink.dispatch(code, KeyEvent.ACTION_DOWN, 0);
      }
    }

    void release() {
      if (held) {
        held = false;
        sink.dispatch(code, KeyEvent.ACTION_UP, 0);
      }
    }
  }
}
