package app.aleatorika;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class MusicTest {
  @Test
  public void attemptsAreLimitedAndRepeatedWrongTapsAreFree() {
    AttemptRound r = new AttemptRound(3);
    assertEquals(AttemptRound.Result.RETRY, r.guess(100, false));
    assertEquals(2, r.left());
    assertEquals(AttemptRound.Result.IGNORED, r.guess(100, false));
    assertEquals(2, r.left());
    assertEquals(AttemptRound.Result.RETRY, r.guess(101, false));
    assertEquals(AttemptRound.Result.CORRECT, r.guess(102, true));
    assertTrue(r.resolved);
    assertEquals(AttemptRound.Result.IGNORED, r.guess(103, true));
  }

  @Test
  public void thirdWrongRevealsOnlyOnce() {
    AttemptRound r = new AttemptRound(3);
    r.guess(1, false);
    r.guess(2, false);
    assertFalse(r.resolved);
    assertEquals(AttemptRound.Result.EXHAUSTED, r.guess(3, false));
    assertEquals(0, r.left());
    assertEquals(AttemptRound.Result.IGNORED, r.guess(4, true));
  }

  @Test
  public void targetIsPlayableInExactOctave() {
    Random random = new Random(31);
    int previous = -1;
    for (int range : new int[] {5, 12, 19, 24})
      for (int i = 0; i < 1000; i++) {
        Music.Position p = Music.pick(random, range, previous);
        assertEquals(p.midi(), Music.OPEN[6 - p.string()] + p.fret());
        assertTrue(p.fret() >= 0 && p.fret() <= range);
        assertNotEquals(previous, p.midi() % 12);
        previous = p.midi() % 12;
      }
  }

  @Test
  public void labelsUseInternationalBAndConcertOctaves() {
    assertEquals("Ми / E2", Music.label(40, true));
    assertEquals("Си / B3", Music.label(59, true));
    assertEquals("Первая октава · 4", Music.octave(60));
  }

  @Test
  public void replayIsDeterministic() {
    assertArrayEquals(Music.pcm(new int[] {40}, 44100), Music.pcm(new int[] {40}, 44100));
    assertArrayEquals(
        Music.pcm(Music.CHORDS[5].midis(), 44100), Music.pcm(Music.CHORDS[5].midis(), 44100));
  }

  @Test
  public void pitchDetectsGuitarRangeAndRejectsSilence() {
    for (int midi = 40; midi <= 76; midi++) {
      float[] x = tone(new int[] {midi}, 8192);
      double f = Music.pitch(x, 44100);
      assertTrue("No pitch " + midi, f > 0);
      double detected = 69 + 12 * Math.log(f / 440) / Math.log(2);
      assertEquals("MIDI " + midi, midi, detected, .12);
    }
    assertEquals(0, Music.pitch(new float[8192], 44100), 0);
  }

  @Test
  public void chordClassifierDetectsEightOpenVoicings() {
    for (int i = 0; i < 8; i++) {
      float[] x = tone(Music.CHORDS[i].midis(), 16384);
      assertEquals(Music.CHORDS[i].id, i, Music.chord(Music.spectrum(x), 44100, x.length));
    }
  }

  @Test
  public void chordClassifierRejectsSingleNoteAndSilence() {
    float[] x = tone(new int[] {40}, 16384);
    assertEquals(-1, Music.chord(Music.spectrum(x), 44100, x.length));
    assertEquals(-1, Music.chord(Music.spectrum(new float[16384]), 44100, 16384));
  }

  static float[] tone(int[] midis, int n) {
    float[] x = new float[n];
    for (int i = 0; i < n; i++)
      for (int midi : midis)
        for (int h = 1; h <= 4; h++)
          x[i] +=
              .4
                  / midis.length
                  / Math.pow(h, 2.2)
                  * Math.sin(2 * Math.PI * 440 * Math.pow(2, (midi - 69) / 12.0) * h * i / 44100);
    return x;
  }

  @Test
  public void sharpsAndFlatsNameSamePitch() {
    try {
      Music.setFlats(false);
      assertEquals("До♯ / C♯4", Music.label(61, true));
      Music.setFlats(true);
      assertEquals("Ре♭ / D♭4", Music.label(61, true));
      assertEquals("Си♭ / B♭3", Music.label(58, true));
      assertEquals("Третья октава · 6", Music.octave(88));
    } finally {
      Music.setFlats(false);
    }
  }

  @Test
  public void compactFretsKeepTouchTargetsAndShrink() {
    for (int range : new int[] {5, 12, 19, 24}) {
      double previous = 1000;
      for (int f = 1; f <= range; f++) {
        double height = Music.fretSpace(range, f);
        assertTrue(height >= 44 && height <= 72);
        assertTrue(height < previous);
        previous = height;
      }
    }
  }

  @Test
  public void chromaticTargetsCoverTwelvePitches() {
    Set<Integer> pcs = new HashSet<>();
    Random r = new Random(17);
    for (int i = 0; i < 1000; i++) pcs.add(Music.pick(r, 24, -1).midi() % 12);
    assertEquals(12, pcs.size());
  }

  @Test
  public void generatedPlucksStayInTune() {
    for (int midi = 40; midi <= 76; midi++) {
      short[] pcm = Music.pcm(new int[] {midi}, 44100);
      float[] x = new float[8192];
      for (int j = 0; j < x.length; j++) x[j] = pcm[j + 10000] / 32768f;
      double f = Music.pitch(x, 44100);
      assertTrue(f > 0);
      assertEquals(midi, 69 + 12 * Math.log(f / 440) / Math.log(2), .23);
    }
    for (int i = 0; i < 8; i++) {
      short[] pcm = Music.pcm(Music.CHORDS[i].midis(), 44100);
      float[] x = new float[16384];
      for (int j = 0; j < x.length; j++) x[j] = pcm[j + 16000] / 32768f;
      assertEquals(Music.CHORDS[i].id, i, Music.chord(Music.spectrum(x), 44100, x.length));
    }
  }
  @Test
  public void allFiftySixVoicingsRecognizedWithFastWindow() {
    for(int ci=0;ci<Music.CHORDS.length;ci++) {
      Set<String> seen=new HashSet<>();
      Music.Chord c=Music.CHORDS[ci];
      Set<Integer> triad=new HashSet<>(Arrays.asList(c.root,(c.root+(c.minor?3:4))%12,(c.root+7)%12));
      for(Music.Chord shape:Music.voicings(c)) {
        assertTrue(seen.add(Arrays.toString(shape.frets)));
        Set<Integer> actual=new HashSet<>();for(int midi:shape.midis())actual.add(midi%12);
        assertEquals(triad,actual);
        float[] signal=tone(shape.midis(),8192);
        assertEquals(c.id+" "+Arrays.toString(shape.frets),ci,Music.chord(Music.spectrum(signal),44100,8192));
      }
    }
  }
}
