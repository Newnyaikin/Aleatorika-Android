package app.aleatorika;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class ChordView extends View {
  private final Music.Chord chord;
  private final boolean dark;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

  public ChordView(Context c, Music.Chord chord, boolean dark) {
    super(c);
    this.chord = chord;
    this.dark = dark;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float sx = getWidth() / 128f, sy = getHeight() / 150f;
    canvas.save();
    canvas.scale(sx, sy);
    int ink = Color.parseColor(dark ? "#C4C1BA" : "#68665F"),
        accent = Color.parseColor(dark ? "#80BAFF" : "#176CB5");
    int base = 1, max = 0, min = 99;
    for (int f : chord.frets) if (f > 0) { max = Math.max(max, f); min = Math.min(min, f); }
    if (max > 4) base = min;
    p.setColor(ink);
    p.setStrokeWidth(1);
    for (int s = 0; s < 6; s++) canvas.drawLine(22 + s * 18, 30, 22 + s * 18, 130, p);
    for (int f = 0; f <= 4; f++) {
      p.setStrokeWidth(f == 0 && base == 1 ? 3 : 1);
      canvas.drawLine(22, 30 + f * 25, 112, 30 + f * 25, p);
    }
    p.setTextAlign(Paint.Align.CENTER);
    p.setTextSize(16);
    for (int s = 0; s < 6; s++) {
      int fret = chord.frets[s];
      if (fret < 1) {
        p.setColor(ink);
        canvas.drawText(fret < 0 ? "×" : "○", 22 + s * 18, 20, p);
      } else {
        p.setColor(accent);
        canvas.drawCircle(22 + s * 18, 30 + (fret - base + .5f) * 25, 5.5f, p);
      }
    }
    int[] barre=Music.barre(chord);
    if(barre!=null){p.setColor(accent);p.setStrokeWidth(10);p.setStrokeCap(Paint.Cap.ROUND);float y=30+(barre[0]-base+.5f)*25;canvas.drawLine(22+barre[1]*18,y,22+barre[2]*18,y,p);p.setStrokeCap(Paint.Cap.BUTT);}
    p.setColor(ink); p.setTextSize(10); p.setTextAlign(Paint.Align.RIGHT);
    canvas.drawText(String.valueOf(base), 14, 46, p);
    p.setTextAlign(Paint.Align.CENTER); canvas.drawText("6",22,146,p); canvas.drawText("1",112,146,p);
    canvas.restore();
  }
}
