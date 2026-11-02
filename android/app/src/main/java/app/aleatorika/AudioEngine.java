package app.aleatorika;

import android.media.*;
import java.util.*;
import java.util.concurrent.*;

/** Android AudioTrack/AudioRecord only; audio never leaves the device. */
public final class AudioEngine implements AutoCloseable {
  public interface Listener {
    void sample(String key);

    void level(float[] envelope, float rms, float peak);

    void error(String message);
  }

  private static final int RATE = 44100;
  private final Map<String, short[]> cache = new ConcurrentHashMap<>();
  private final ExecutorService playback = Executors.newSingleThreadExecutor();
  private AudioTrack track;
  private volatile int playToken = 0, recordToken = 0;
  private volatile AudioRecord recorder;

  public int play(int[] midis) {return play(midis,false);}

  public int play(int[] midis, boolean compact) {
    stopSound();
    final int token = playToken;
    final short[] pcm = cache.computeIfAbsent(Arrays.toString(midis)+":"+compact, s -> {
      short[] full=Music.pcm(midis,RATE);if(!compact)return full;
      short[] shortSample=Arrays.copyOf(full,(int)(RATE*.72));int fade=(int)(RATE*.10);
      for(int i=shortSample.length-fade;i<shortSample.length;i++)shortSample[i]=(short)(shortSample[i]*(shortSample.length-i)/(float)fade);
      return shortSample;
    });
    playback.execute(
        () -> {
          synchronized (this) {
            if (token != playToken) return;
            try {
              AudioTrack t =
                  new AudioTrack.Builder()
                      .setAudioAttributes(
                          new AudioAttributes.Builder()
                              .setUsage(AudioAttributes.USAGE_MEDIA)
                              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                              .build())
                      .setAudioFormat(
                          new AudioFormat.Builder()
                              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                              .setSampleRate(RATE)
                              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                              .build())
                      .setBufferSizeInBytes(pcm.length * 2)
                      .setTransferMode(AudioTrack.MODE_STATIC)
                      .build();
              t.write(pcm, 0, pcm.length);
              track = t;
              t.play();
            } catch (Exception ignored) {
            }
          }
        });
    return Math.round(pcm.length * 1000f / RATE);
  }

  public synchronized void stopSound() {
    playToken++;
    if (track != null) {
      try {
        track.stop();
        track.release();
      } catch (Exception ignored) {
      }
      track = null;
    }
  }

  public void listen(boolean chords, Listener listener) {
    listen(chords, false, listener);
  }

  public void listen(boolean chords, boolean exactNotes, Listener listener) {
    stopMic();
    final int token = recordToken;
    new Thread(
            () -> {
              AudioRecord record = null;
              try {
                int n = chords ? 8192 : 4096;
                int hop = 2048;
                int min =
                    AudioRecord.getMinBufferSize(
                        RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                record =
                    new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min, n * 4));
                synchronized (this) {
                  if (token != recordToken) {
                    record.release();
                    return;
                  }
                  recorder = record;
                }
                if (record.getState() != AudioRecord.STATE_INITIALIZED)
                  throw new IllegalStateException();
                record.startRecording();
                short[] raw = new short[n];
                boolean primed = false;
                while (token == recordToken) {
                  if (primed) System.arraycopy(raw, hop, raw, 0, n - hop);
                  int got = primed ? n - hop : 0;
                  while (got < n && token == recordToken) {
                    int r = record.read(raw, got, Math.min(1024, n - got));
                    if (r <= 0) {
                      if (token != recordToken) break;
                      throw new IllegalStateException("AudioRecord read failed: " + r);
                    }
                    float[] envelope = new float[17];
                    double sum = 0;
                    float peak = 0;
                    for (int j = 0; j < r; j++) {
                      float value = Math.abs(raw[got + j] / 32768f);
                      sum += value * value;
                      peak = Math.max(peak, value);
                      int bin = Math.min(16, j * 17 / r);
                      envelope[bin] = Math.max(envelope[bin], value);
                    }
                    if (token == recordToken)
                      listener.level(envelope, (float) Math.sqrt(sum / r), peak);
                    got += r;
                  }
                  if (got < n) continue;
                  primed = true;
                  float[] samples = new float[n];
                  double energy = 0;
                  for (int i = 0; i < n; i++) {
                    samples[i] = raw[i] / 32768f;
                    energy += samples[i] * samples[i];
                  }
                  String key = null;
                  if (Math.sqrt(energy / n) >= .008) {
                    if (chords) {
                      int c = Music.chord(Music.spectrum(samples), RATE, n);
                      if (c >= 0) key = Music.CHORDS[c].id;
                    } else {
                      double f = Music.pitch(samples, RATE);
                      if (f > 0) {
                        double exact = 69 + 12 * Math.log(f / 440) / Math.log(2);
                        int midi = (int) Math.round(exact);
                        if (Math.abs(exact - midi) < .4)
                          key = String.valueOf(exactNotes ? midi : Math.floorMod(midi, 12));
                      }
                    }
                  }
                  if (token == recordToken) listener.sample(key);
                }
              } catch (Exception e) {
                if (token == recordToken)
                  listener.error(
                      "Не удалось включить микрофон. Используй ручное подтверждение или проверь"
                          + " разрешение.");
              } finally {
                if (record != null) {
                  synchronized (this) {
                    if (recorder == record) recorder = null;
                  }
                  try {
                    record.stop();
                  } catch (Exception ignored) {
                  }
                  try {
                    record.release();
                  } catch (Exception ignored) {
                  }
                }
              }
            },
            "Aleatorika-microphone")
        .start();
  }

  public synchronized void stopMic() {
    recordToken++;
    AudioRecord r = recorder;
    if (r != null)
      try {
        r.stop();
      } catch (Exception ignored) {
      }
  }

  @Override
  public void close() {
    stopSound();
    stopMic();
    playback.shutdownNow();
  }
}
