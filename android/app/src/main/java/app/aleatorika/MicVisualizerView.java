package app.aleatorika;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Microphone amplitude only: no decorative or random animation, no audio persistence. */
public final class MicVisualizerView extends View {
  private final Paint paint = new Paint(3);
  private final int accent, muted, red;
  private final float[] bars = new float[17];
  private float rms = 0, peak = 0;
  private boolean active = false;
  private String label = "Микрофон выключен";

  public MicVisualizerView(Context c, int accent, int muted, int red) {
    super(c);
    this.accent = accent;
    this.muted = muted;
    this.red = red;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  private float dp(float n) {
    return n * getResources().getDisplayMetrics().density;
  }

  public void update(float[] envelope, float rms, float peak, boolean active) {
    this.rms = rms;
    this.peak = peak;
    this.active = active;
    for (int i = 0; i < 17; i++) bars[i] = active ? Math.max(envelope[i], bars[i] * .63f) : 0;
    String next =
        !active
            ? "Микрофон выключен"
            : peak >= .98
                ? "Слишком громко — отодвинь телефон"
                : rms < .004 ? "Микрофон включён · тихо" : "Микрофон слышит звук";
    if (!next.equals(label)) {
      label = next;
      setContentDescription(label);
    }
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(),
        cy = Math.min(dp(36), getHeight() * .36f),
        barw = dp(5),
        gap = dp(5),
        left = (w - (barw + gap) * 17 + gap) / 2;
    paint.setColor(peak >= .98 ? red : active ? accent : muted);
    for (int i = 0; i < 17; i++) {
      float level =
          active
              ? Math.max(
                  0, Math.min(1, (float) ((20 * Math.log10(Math.max(1e-5, bars[i])) + 60) / 54)))
              : 0;
      float h = dp(4) + Math.min(dp(48),getHeight()*.45f)*level;
      c.drawRoundRect(
          left + i * (barw + gap),
          cy - h / 2,
          left + i * (barw + gap) + barw,
          cy + h / 2,
          dp(3),
          dp(3),
          paint);
    }
    paint.setColor(peak >= .98 ? red : muted);
    paint.setTextSize(dp(14));
    paint.setTextAlign(Paint.Align.CENTER);
    c.drawText(label, w / 2, Math.min(dp(85),getHeight()-dp(8)), paint);
  }
}
