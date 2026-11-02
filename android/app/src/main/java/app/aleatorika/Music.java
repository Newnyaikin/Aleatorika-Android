package app.aleatorika;

import java.util.*;

/** Pure Java music model and local DSP, shared by the native UI and JVM tests. */
public final class Music {
  public static final int[] OPEN = {40, 45, 50, 55, 59, 64};
  public static String[] RU = {
    "До", "До♯", "Ре", "Ре♯", "Ми", "Фа", "Фа♯", "Соль", "Соль♯", "Ля", "Ля♯", "Си"
  };
  public static String[] EN = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
  public static final int[] CHROMATIC = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
  private static final String[] SHARP_RU = RU, SHARP_EN = EN;
  private static final String[] FLAT_RU = {
    "До", "Ре♭", "Ре", "Ми♭", "Ми", "Фа", "Соль♭", "Соль", "Ля♭", "Ля", "Си♭", "Си"
  };
  private static final String[] FLAT_EN = {
    "C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B"
  };

  public static void setFlats(boolean flats) {
    RU = flats ? FLAT_RU : SHARP_RU;
    EN = flats ? FLAT_EN : SHARP_EN;
  }

  public static double fretSpace(int range, int fret) {
    return 44 + 28 * (1 - Math.exp(-(44 * Math.pow(2, (range - fret) / 12.0) - 44) / 40));
  }

  public static final int[] NATURAL = {0, 2, 4, 5, 7, 9, 11};

  public static final class Chord {
    public final String id;
    public final int root;
    public final boolean minor;
    public final int[] frets;

    Chord(String id, int root, boolean minor, int... f) {
      this.id = id;
      this.root = root;
      this.minor = minor;
      this.frets = f;
    }

    public int[] midis() {
      return java.util.stream.IntStream.range(0, 6)
          .filter(i -> frets[i] >= 0)
          .map(i -> OPEN[i] + frets[i])
          .toArray();
    }

    public String name() {
      return RU[root] + (minor ? " минор" : " мажор") + " / " + id;
    }
  }

  public static final Chord[] CHORDS = {
    new Chord("C", 0, false, -1, 3, 2, 0, 1, 0),
    new Chord("D", 2, false, -1, -1, 0, 2, 3, 2),
    new Chord("E", 4, false, 0, 2, 2, 1, 0, 0),
    new Chord("G", 7, false, 3, 2, 0, 0, 0, 3),
    new Chord("A", 9, false, -1, 0, 2, 2, 2, 0),
    new Chord("Am", 9, true, -1, 0, 2, 2, 1, 0),
    new Chord("Dm", 2, true, -1, -1, 0, 2, 3, 1),
    new Chord("Em", 4, true, 0, 2, 2, 0, 0, 0),
    new Chord("F", 5, false, 1, 3, 3, 2, 1, 1),
    new Chord("Fm", 5, true, 1, 3, 3, 1, 1, 1),
    new Chord("B", 11, false, 7, 9, 9, 8, 7, 7),
    new Chord("Bm", 11, true, 7, 9, 9, 7, 7, 7),
    new Chord("B♭", 10, false, 6, 8, 8, 7, 6, 6),
    new Chord("B♭m", 10, true, 6, 8, 8, 6, 6, 6)
  };

  /** Open + movable E/A/D shapes, each with exactly the same triad. */
  public static Chord[] voicings(Chord c) {
    int e = Math.floorMod(c.root - 4, 12), a = Math.floorMod(c.root - 9, 12), d = Math.floorMod(c.root - 2, 12);
    if (e == 0) e = 12; if (a == 0) a = 12; if (d == 0) d = 12;
    if(Arrays.asList("F","Fm","B","Bm","B♭","B♭m").contains(c.id))return new Chord[]{c,
      new Chord(c.id,c.root,c.minor,e+12,e+14,e+14,e+12+(c.minor?0:1),e+12,e+12),
      new Chord(c.id,c.root,c.minor,-1,a,a+2,a+2,a+(c.minor?1:2),a),
      new Chord(c.id,c.root,c.minor,-1,-1,d,d+2,d+3,d+(c.minor?1:2))};
    return new Chord[] {c,
      new Chord(c.id,c.root,c.minor,e,e+2,e+2,e+(c.minor?0:1),e,e),
      new Chord(c.id,c.root,c.minor,-1,a,a+2,a+2,a+(c.minor?1:2),a),
      new Chord(c.id,c.root,c.minor,-1,-1,d,d+2,d+3,d+(c.minor?1:2))};
  }

  /** Lowest-fret full or partial barre, excluding open-position shortcuts. */
  public static int[] barre(Chord c){
    int fret=99,first=-1,last=-1;
    for(int f:c.frets){if(f==0)return null;if(f>0)fret=Math.min(fret,f);}
    for(int i=0;i<6;i++)if(c.frets[i]==fret){if(first<0)first=i;last=i;}
    if(last-first<2)return null;
    for(int i=first;i<=last;i++)if(c.frets[i]<fret)return null;
    return new int[]{fret,first,last};
  }

  public record Position(int string, int fret, int midi) {}

  public static String label(int midi, boolean octave) {
    int pc = Math.floorMod(midi, 12);
    return RU[pc] + " / " + EN[pc] + (octave ? String.valueOf(midi / 12 - 1) : "");
  }

  public static String octave(int midi) {
    int o = midi / 12 - 1;
    return (o == 2
            ? "Большая октава"
            : o == 3
                ? "Малая октава"
                : o == 4
                    ? "Первая октава"
                    : o == 5
                        ? "Вторая октава"
                        : o == 6 ? "Третья октава" : o == 7 ? "Четвёртая октава" : "Октава")
        + " · "
        + o;
  }

  public static List<Position> positions(int range) {
    List<Position> p = new ArrayList<>();
    for (int s = 1; s <= 6; s++)
      for (int f = 0; f <= range; f++) {
        int midi = OPEN[6 - s] + f;
        p.add(new Position(s, f, midi));
      }
    return p;
  }

  public static Position pick(Random r, int range, int previousPc) {
    List<Position> p = positions(range);
    List<Integer> pcs = new ArrayList<>();
    for (int pc : CHROMATIC) if (pc != previousPc) pcs.add(pc);
    int pc = pcs.get(r.nextInt(pcs.size()));
    List<Integer> mids = new ArrayList<>();
    for (Position n : p) if (n.midi % 12 == pc && !mids.contains(n.midi)) mids.add(n.midi);
    int midi = mids.get(r.nextInt(mids.size()));
    for (Position n : p) if (n.midi == midi) return n;
    throw new IllegalStateException();
  }

  public static short[] pcm(int[] midis, int rate) {
    int len = (int) (rate * (midis.length == 1 ? 1.65 : 2.1));
    double[] mix = new double[len];
    for (int k = 0; k < midis.length; k++) {
      int midi = midis[k], offset = (int) (k * .035 * rate);
      double freq = 440 * Math.pow(2, (midi - 69) / 12.0);
      int n = Math.max(2, (int) Math.round(rate / freq + .5));
      double[] ring = new double[n];
      Random r = new Random(midi * 2654435761L);
      for (int i = 0; i < n; i++)
        ring[i] = .7 * Math.sin(2 * Math.PI * i / n) + .3 * (r.nextDouble() * 2 - 1);
      for (int i = 0; i < len - offset; i++) {
        int j = i % n;
        double val = ring[j];
        ring[j] = .996 * (val + ring[(j + 1) % n]) * .5;
        double fade =
            Math.min(1, i / (rate * .004)) * Math.min(1, (len - offset - i) / (rate * .12));
        mix[i + offset] += val * fade * .78 / midis.length;
      }
    }
    short[] out = new short[len];
    for (int i = 0; i < len; i++)
      out[i] = (short) Math.max(-32767, Math.min(32767, mix[i] * 32767));
    return out;
  }

  public static double pitch(float[] input, int rate) {
    double rms = 0;
    for (float v : input) rms += v * v;
    if (Math.sqrt(rms / input.length) < .008) return 0;
    int sr = rate / 2, len = input.length / 2;
    float[] a = new float[len];
    for (int i = 0; i < len; i++) a[i] = (input[i * 2] + input[i * 2 + 1]) / 2;
    int max = Math.min(sr / 65, len / 2), min = sr / 1100, w = len - max;
    double[] d = new double[max + 1];
    double cum = 0;
    int best = -1;
    for (int tau = 1; tau <= max; tau++) {
      double sum = 0;
      for (int i = 0; i < w; i++) {
        double delta = a[i] - a[i + tau];
        sum += delta * delta;
      }
      cum += sum;
      d[tau] = cum > 0 ? sum * tau / cum : 1;
    }
    for (int t = min; t < max; t++)
      if (d[t] < .14) {
        while (t + 1 < max && d[t + 1] < d[t]) t++;
        best = t;
        break;
      }
    if (best < 0) return 0;
    double x = d[best - 1],
        y = d[best],
        z = d[best + 1],
        den = 2 * (2 * y - z - x),
        shift = den != 0 ? (z - x) / den : 0;
    return sr / (best + shift);
  }

  public static float[] spectrum(float[] data) {
    int n = data.length;
    double[] re = new double[n], im = new double[n];
    for (int i = 0; i < n; i++) re[i] = data[i] * (.5 - .5 * Math.cos(2 * Math.PI * i / (n - 1)));
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double a = re[i];
        re[i] = re[j];
        re[j] = a;
      }
    }
    for (int l = 2; l <= n; l <<= 1) {
      double angle = -2 * Math.PI / l, wr0 = Math.cos(angle), wi0 = Math.sin(angle);
      for (int i = 0; i < n; i += l) {
        double wr = 1, wi = 0;
        for (int j = 0; j < l / 2; j++) {
          int a = i + j, b = a + l / 2;
          double tr = wr * re[b] - wi * im[b], ti = wr * im[b] + wi * re[b];
          re[b] = re[a] - tr;
          im[b] = im[a] - ti;
          re[a] += tr;
          im[a] += ti;
          double temp = wr * wr0 - wi * wi0;
          wi = wr * wi0 + wi * wr0;
          wr = temp;
        }
      }
    }
    float[] db = new float[n / 2];
    for (int i = 0; i < db.length; i++)
      db[i] = (float) (20 * Math.log10(Math.max(1e-12, Math.hypot(re[i], im[i]) * 4 / n)));
    return db;
  }

  private record Peak(int midi, double weight) {}

  public static int chord(float[] db, int rate, int fft) {
    List<Peak> peaks = new ArrayList<>();
    double strongest = 0;
    for (int k = 2; k < db.length - 1; k++) {
      double f = (double) k * rate / fft;
      if (f < 73 || f > 1700 || db[k] < -78 || db[k] < db[k - 1] || db[k] < db[k + 1]) continue;
      double a = db[k - 1],
          b = db[k],
          c = db[k + 1],
          den = a - 2 * b + c,
          off = den != 0 ? .5 * (a - c) / den : 0;
      f = (k + Math.max(-.5, Math.min(.5, off))) * rate / fft;
      double exact = 69 + 12 * Math.log(f / 440) / Math.log(2);
      int m = (int) Math.round(exact);
      if (Math.abs(exact - m) > .38) continue;
      double weight = Math.pow(10, db[k] / 25.0) / Math.pow(f / 100, .12);
      strongest = Math.max(strongest, weight);
      peaks.add(new Peak(m, weight));
    }
    if (peaks.isEmpty()) return -1;
    double[] chroma = new double[12];
    int bass = -1;
    for (Peak p : peaks) {
      if (p.weight > strongest * .035) chroma[Math.floorMod(p.midi, 12)] += p.weight;
      if (p.midi <= 62 && p.weight > strongest * .18 && (bass < 0 || p.midi < bass)) bass = p.midi;
    }
    double norm = 0;
    for (double v : chroma) norm += v * v;
    norm = Math.sqrt(norm);
    if (norm == 0) return -1;
    double best = -1, second = -1, bestSim = 0;
    int result = -1;
    boolean bestCovered = false;
    for (int ci = 0; ci < CHORDS.length; ci++) {
      Chord ch = CHORDS[ci];
      double sim = 0;
      for (Chord voicing : voicings(ch)) {
        double[] v = new double[12];
        for (int m : voicing.midis())
          for (int h=1;h<=4;h++)
            v[Math.floorMod((int)Math.round(m+12*Math.log(h)/Math.log(2)),12)] += 1/Math.pow(h,2.2);
        double dot=0,vnorm=0;
        for(int i=0;i<12;i++){dot+=chroma[i]*v[i];vnorm+=v[i]*v[i];}
        sim=Math.max(sim,dot/(norm*Math.sqrt(vnorm)));
      }
      double score = sim * .93 + (bass >= 0 && bass % 12 == ch.root ? .07 : 0);
      boolean covered = true;
      for (int pc : new int[] {ch.root, (ch.root + (ch.minor ? 3 : 4)) % 12, (ch.root + 7) % 12}) {
        boolean has = false;
        for (Peak p : peaks)
          if (p.midi % 12 == pc && p.weight > strongest * .06)
            has = true;
        if (!has) covered = false;
      }
      if (score > best) {
        second = best;
        best = score;
        bestSim = sim;
        result = ci;
        bestCovered = covered;
      } else second = Math.max(second, score);
    }
    return bestCovered && bestSim >= .8 && best - second >= .025 ? result : -1;
  }
}
