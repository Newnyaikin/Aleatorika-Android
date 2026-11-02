package app.aleatorika;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.*;
import java.util.*;

public final class FretView extends View {
  public interface Tap {
    void position(int string, int fret);
  }

  private final int range, target;
  private final boolean reveal, dark, locked;
  private final Set<Integer> wrong;
  private final Tap callback;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float[] edges;
  private float downX, downY;
  private int virtualFocus = -1;
  private final int ink, muted, surface, green, red;
  private float left, cell, top, nut;

  public FretView(
      Context c,
      int range,
      int target,
      boolean reveal,
      boolean dark,
      boolean locked,
      Set<Integer> wrong,
      Tap cb) {
    super(c);
    this.range = range;
    this.target = target;
    this.reveal = reveal;
    this.dark = dark;
    this.locked = locked;
    this.wrong = new HashSet<>(wrong);
    callback = cb;
    ink = Color.parseColor(dark ? "#FFFFFF" : "#2C2C2B");
    muted = Color.parseColor(dark ? "#BAB8B3" : "#67655F");
    surface = Color.parseColor(dark ? "#202020" : "#FFFFFF");
    green = Color.parseColor(dark ? "#8BD7A6" : "#25764B");
    red = Color.parseColor(dark ? "#FFB0A6" : "#B7352B");
    setFocusable(true);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setContentDescription("Гитарный гриф. 6-я струна слева, 1-я справа. Лады от 0 до " + range);
  }

  private float dp(float n) {
    return n * getResources().getDisplayMetrics().density;
  }

  private float sp(float n) {
    return n * getResources().getDisplayMetrics().scaledDensity;
  }

  @Override
  protected void onMeasure(int w, int h) {
    int width = MeasureSpec.getSize(w);
    double sum = 0;
    for (int f = 1; f <= range; f++) sum += Music.fretSpace(range, f);
    int desired = (int) dp((float) (72 + sum));
    int height =
        MeasureSpec.getMode(h) == MeasureSpec.EXACTLY
            ? MeasureSpec.getSize(h)
            : Math.max(desired, getMinimumHeight());
    setMeasuredDimension(width, height);
  }

  private void geometry() {
    left = dp(28);
    cell = (getWidth() - left) / 6;
    top = dp(28);
    nut = top + dp(44);
    double sum = 0;
    for (int f = 1; f <= range; f++) sum += Music.fretSpace(range, f);
    double unit = Math.max(getResources().getDisplayMetrics().density, (getHeight() - nut) / sum);
    edges = new float[range + 1];
    edges[0] = nut;
    for (int f = 1; f <= range; f++)
      edges[f] = edges[f - 1] + (float) (unit * Music.fretSpace(range, f));
  }

  private float x(int s) {
    return left + (6 - s + .5f) * cell;
  }

  private float y(int f) {
    return f == 0 ? (top + nut) / 2 : (edges[f - 1] + edges[f]) / 2;
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    geometry();
    c.drawColor(surface);
    float end = edges[range];
    p.setShader(
        new LinearGradient(
            left,
            0,
            getWidth(),
            0,
            new int[] {0xff4c382a, 0xff30231c, 0xff4a3528},
            null,
            Shader.TileMode.CLAMP));
    c.drawRect(left, nut, getWidth(), end, p);
    p.setShader(null);
    p.setColor(0x11d8b680);
    p.setStrokeWidth(dp(.5f));
    for (float a = left + dp(9); a < getWidth(); a += dp(17)) c.drawLine(a, nut, a + dp(4), end, p);
    p.setColor(muted);
    p.setTextAlign(Paint.Align.CENTER);
    p.setTextSize(sp(14));
    c.drawText("лад", left / 2, dp(18), p);
    for (int s = 6; s >= 1; s--) c.drawText(String.valueOf(s), x(s), dp(18), p);
    for (int f = 0; f <= range; f++) c.drawText(String.valueOf(f), left / 2, y(f) + sp(5), p);
    p.setColor(0xffd8cbb1);
    for (int f : new int[] {3, 5, 7, 9, 15, 17, 19, 21})
      if (f <= range) c.drawCircle((left + getWidth()) / 2, y(f), dp(5), p);
    for (int marker : new int[] {12, 24})
      if (range >= marker) {
        c.drawCircle(left + (getWidth() - left) / 3, y(marker), dp(5), p);
        c.drawCircle(left + (getWidth() - left) * 2 / 3, y(marker), dp(5), p);
      }
    for (int f = 1; f <= range; f++) {
      p.setColor(0xffaaa292);
      p.setStrokeWidth(dp(3));
      c.drawLine(left, edges[f], getWidth(), edges[f], p);
      p.setColor(0xffdfd7c9);
      p.setStrokeWidth(dp(.6f));
      c.drawLine(left, edges[f] - dp(1), getWidth(), edges[f] - dp(1), p);
    }
    p.setColor(0xffe1d8c4);
    c.drawRect(left, nut - dp(3), getWidth(), nut + dp(3), p);
    for (int s = 6; s >= 1; s--) {
      p.setColor(0xffdbd4c6);
      p.setStrokeWidth(dp(1 + (s - 1) * .22f));
      c.drawLine(x(s), nut + dp(3), x(s), end, p);
      p.setStyle(Paint.Style.STROKE);
      p.setColor(muted);
      p.setStrokeWidth(dp(1));
      c.drawCircle(x(s), y(0), dp(6), p);
      p.setStyle(Paint.Style.FILL);
    }
    for (int s = 1; s <= 6; s++)
      for (int f = 0; f <= range; f++) {
        boolean hit = reveal && Music.OPEN[6 - s] + f == target, miss = wrong.contains(s * 100 + f);
        if (!hit && !miss) continue;
        float yy = y(f), r = dp(14);
        p.setColor(hit ? green : red);
        c.drawCircle(x(s), yy, r, p);
        p.setColor(dark ? 0xff17251c : Color.WHITE);
        p.setTextSize(sp(20));
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.drawText(hit ? "✓" : "×", x(s), yy + sp(7), p);
        p.setTypeface(Typeface.DEFAULT);
      }
    int scroll =
        getParent() instanceof android.widget.ScrollView
            ? ((android.widget.ScrollView) getParent()).getScrollY()
            : 0;
    if (scroll > 0) {
      p.setColor(surface);
      c.drawRect(0, scroll, getWidth(), scroll + top, p);
      p.setColor(muted);
      p.setTextSize(sp(14));
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText("лад", left / 2, scroll + dp(18), p);
      for (int s = 6; s >= 1; s--) c.drawText(String.valueOf(s), x(s), scroll + dp(18), p);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      downX = event.getX();
      downY = event.getY();
      return true;
    }
    if (event.getAction() == MotionEvent.ACTION_UP) {
      if (Math.hypot(event.getX() - downX, event.getY() - downY) < dp(12)) {
        geometry();
        int scroll =
            getParent() instanceof android.widget.ScrollView
                ? ((android.widget.ScrollView) getParent()).getScrollY()
                : 0;
        if (event.getY() < scroll + top) return true;
        int col = (int) ((event.getX() - left) / cell);
        if (event.getX() < left || col < 0 || col > 5) return true;
        int fret = -1;
        if (event.getY() >= top && event.getY() < nut) fret = 0;
        else
          for (int f = 1; f <= range; f++)
            if (event.getY() >= edges[f - 1] && event.getY() < edges[f]) {
              fret = f;
              break;
            }
        if (fret >= 0 && !locked && !wrong.contains((6 - col) * 100 + fret)) {
          callback.position(6 - col, fret);
          performClick();
        }
      }
      return true;
    }
    return true;
  }

  public int answerScroll() {
    geometry();
    for (int f = 0; f <= range; f++)
      for (int s = 1; s <= 6; s++)
        if (Music.OPEN[6 - s] + f == target)
          return Math.max(0, Math.round((f == 0 ? top : edges[f - 1]) - top));
    return 0;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  private Rect bounds(int s, int f) {
    geometry();
    return new Rect(
        Math.round(x(s) - cell / 2),
        Math.round(f == 0 ? top : edges[f - 1]),
        Math.round(x(s) + cell / 2),
        Math.round(f == 0 ? nut : edges[f]));
  }

  @Override
  public AccessibilityNodeProvider getAccessibilityNodeProvider() {
    return new AccessibilityNodeProvider() {
      @Override
      public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
        if (id == View.NO_ID) {
          AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain(FretView.this);
          onInitializeAccessibilityNodeInfo(n);
          for (int s = 1; s <= 6; s++)
            for (int f = 0; f <= range; f++) n.addChild(FretView.this, s * 100 + f);
          return n;
        }
        int s = id / 100, f = id % 100;
        if (s < 1 || s > 6 || f > range) return null;
        AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain();
        n.setSource(FretView.this, id);
        n.setParent(FretView.this);
        n.setPackageName(getContext().getPackageName());
        n.setClassName("android.widget.Button");
        boolean hit = reveal && Music.OPEN[6 - s] + f == target, bad = wrong.contains(id);
        n.setContentDescription(
            s
                + "-я струна, "
                + (f == 0 ? "открытая" : f + "-й лад")
                + (hit ? ", правильная позиция" : bad ? ", уже проверено: не совпало" : ""));
        n.setEnabled(!locked && !bad);
        n.setClickable(!locked && !bad);
        n.setFocusable(true);
        n.setVisibleToUser(isShown());
        Rect r = bounds(s, f);
        n.setBoundsInParent(r);
        int[] at = new int[2];
        getLocationOnScreen(at);
        r.offset(at[0], at[1]);
        n.setBoundsInScreen(r);
        n.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        n.addAction(
            id == virtualFocus
                ? AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                : AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        n.setAccessibilityFocused(id == virtualFocus);
        return n;
      }

      @Override
      public boolean performAction(int id, int action, Bundle args) {
        if (id == View.NO_ID) return FretView.this.performAccessibilityAction(action, args);
        int s = id / 100, f = id % 100;
        if (action == AccessibilityNodeInfo.ACTION_CLICK && !locked && !wrong.contains(id)) {
          callback.position(s, f);
          return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
          virtualFocus = id;
          AccessibilityEvent e =
              AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
          e.setSource(FretView.this, id);
          e.setPackageName(getContext().getPackageName());
          e.setClassName("android.widget.Button");
          getParent().requestSendAccessibilityEvent(FretView.this, e);
          return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
          virtualFocus = -1;
          return true;
        }
        return false;
      }
    };
  }
}
