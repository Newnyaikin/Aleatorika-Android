package app.aleatorika;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.*;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w390dp-h844dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeUiTest {
  static void set(MainActivity a, String field, Object value) throws Exception {
    Field f = MainActivity.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(a, value);
  }

  static Object get(MainActivity a, String field) throws Exception {
    Field f = MainActivity.class.getDeclaredField(field);
    f.setAccessible(true);
    return f.get(a);
  }

  static void call(MainActivity a, String name) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(a);
  }

  static void guess(MainActivity a, int s, int f) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod("guessPosition", int.class, int.class);
    m.setAccessible(true);
    m.invoke(a, s, f);
  }

  static View find(View root, String label) {
    if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int i = 0; i < g.getChildCount(); i++) {
        View found = find(g.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  static void click(MainActivity a, String label) {
    View v = find(a.getWindow().getDecorView(), label);
    assertNotNull(label, v);
    assertTrue(label, v.isEnabled());
    v.performClick();
  }

  static void shot(MainActivity a, String name) throws Exception {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    View v = a.findViewById(android.R.id.content);
    v.measure(
        View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(844, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, 390, 844);
    Bitmap bmp = Bitmap.createBitmap(390, 844, Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(bmp));
    File dir = new File("build/reports/native-ui");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bmp.recycle();
  }

  @Test
  public void nativeScreensAndThreeAttemptsWork() throws Exception {
    ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class);
    MainActivity a = ctl.get();
    Settings.Global.putFloat(
        RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE,
        0);
    ctl.setup();
    try {
      shot(a, "menu");
      set(a, "permissionAsked", true); // denied permission: manual learning remains available
      click(a, "Начать");
      assertEquals("running", get(a, "phase"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
      assertEquals(2, get(a, "step"));
      assertNull(find(a.getWindow().getDecorView(), "Теперь сыграть →"));
      assertNull(find(a.getWindow().getDecorView(), "Включить микрофон"));
      assertNotNull(find(a.getWindow().getDecorView(), "Услышь и повтори ноту"));
      shot(a, "microphone-off");
      set(a, "theme", "dark");
      call(a, "render");
      shot(a, "microphone-dark");
      click(a, "Без проверки");
      assertEquals(3, get(a, "step"));
      shot(a, "note-choices-dark");
      set(a, "experience", 2);
      set(a, "range", 24);
      set(a, "chords", false);
      set(a, "theme", "light");
      set(a, "phase", "menu");
      call(a, "render");
      click(a, "Начать");
      assertTrue((Boolean) get(a, "focusMode"));
      shot(a, "fret24");
      set(a, "target", new Music.Position(1, 24, 88));
      set(a, "playing", false);
      call(a, "render");
      guess(a, 6, 0);
      AttemptRound attempts = (AttemptRound) get(a, "attempts");
      assertEquals(2, attempts.left());
      assertFalse((Boolean) get(a, "answered"));
      shot(a, "attempt-wrong");
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
      guess(a, 5, 0);
      assertEquals(1, attempts.left());
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
      guess(a, 1, 24);
      assertTrue((Boolean) get(a, "answered"));
      assertEquals(1, get(a, "score"));
      shot(a, "attempt-correct");
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750));
      assertEquals(3, ((AttemptRound) get(a, "attempts")).left());
      assertTrue((Boolean) get(a, "playing"));
      a.onBackPressed();
      assertFalse((Boolean) get(a, "focusMode"));
      shot(a, "fret-normal");
    } finally {
      ctl.pause().stop().destroy();
    }
  }

  @Test
  public void microphonePersistsThroughReplayAndAutomaticTransitions() throws Exception {
    shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity a = ctl.get();
    try {
      click(a, "Начать");
      assertEquals(2, get(a, "step"));
      assertTrue((Boolean) get(a, "capturing"));
      int epoch = (Integer) get(a, "captureEpoch");
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
      Method play = MainActivity.class.getDeclaredMethod("playSample", boolean.class);
      play.setAccessible(true);
      play.invoke(a, true);
      assertEquals(epoch, get(a, "captureEpoch"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
      assertTrue((Boolean) get(a, "capturing"));
      assertEquals(epoch, get(a, "captureEpoch"));
      // Feed an input envelope into the actual native Canvas meter, not a web element.
      MicVisualizerView meter = (MicVisualizerView) get(a, "micMeter");
      assertNotNull(meter);
      float[] bins = new float[17];
      for (int i = 0; i < 17; i++) bins[i] = .012f + i * .002f;
      meter.update(bins, .025f, .05f, true);
      shot(a, "microphone-live");
      set(a, "ignoreUntil", 0L);set(a,"sampleNeedsQuiet",false);
      Method detected = MainActivity.class.getDeclaredMethod("onDetected", String.class);
      detected.setAccessible(true);
      String key = String.valueOf(((Music.Position) get(a, "target")).midi() % 12);
      for (int i = 0; i < 3; i++) detected.invoke(a, key);
      assertEquals(3, get(a, "step"));
      assertTrue((Boolean) get(a, "capturing"));
      Method name = MainActivity.class.getDeclaredMethod("guessName", int.class);
      name.setAccessible(true);
      int[] options = (int[]) get(a, "options");
      int correct = 0;
      while (options[correct] != Integer.parseInt(key)) correct++;
      name.invoke(a, correct);
      assertTrue((Boolean) get(a, "answered"));
      assertNull(find(a.getWindow().getDecorView(), "Следующий звук →"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));
      assertEquals(1, get(a, "round"));
      assertEquals(2, get(a, "step"));
      assertTrue((Boolean) get(a, "playing"));
      assertTrue((Boolean) get(a, "capturing"));
      assertEquals(epoch, get(a, "captureEpoch"));
      // Switching away invalidates the scheduled transition.
      a.onBackPressed();
      assertEquals("paused", get(a, "phase"));
      assertFalse((Boolean) get(a, "capturing"));
    } finally {
      ctl.pause().stop().destroy();
    }
  }
  @Test
  public void sightModeChecksExactNotesAndChordsWithoutAutoSample() throws Exception {
    shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity a=ctl.get();
    try {
      click(a,"По схеме");shot(a,"sight-menu");
      click(a,"Начать");
      assertEquals("running",get(a,"phase"));assertEquals(false,get(a,"playing"));
      shot(a,"sight-note");
      Method detected=MainActivity.class.getDeclaredMethod("onDetected",String.class);detected.setAccessible(true);
      Music.Position target=(Music.Position)get(a,"target");
      set(a,"ignoreUntil",0L);set(a,"sampleNeedsQuiet",false);set(a,"capturing",true);
      detected.invoke(a,String.valueOf(target.midi()+12));detected.invoke(a,String.valueOf(target.midi()+12));
      assertEquals(false,get(a,"answered"));
      detected.invoke(a,String.valueOf(target.midi()));detected.invoke(a,String.valueOf(target.midi()));
      assertEquals(true,get(a,"answered"));assertEquals(1,get(a,"score"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
      assertEquals(1,get(a,"round"));assertEquals(false,get(a,"playing"));
      click(a,"Пропустить");assertEquals(1,get(a,"score"));
      click(a,"Итоги");assertEquals(false,get(a,"capturing"));
      set(a,"chords",true);set(a,"phase","menu");call(a,"render");
      click(a,"Начать");
      set(a,"theme","dark");set(a,"chordTarget",7);set(a,"sightChord",Music.voicings(Music.CHORDS[7])[1]);call(a,"render");shot(a,"sight-chord-dark");
      set(a,"ignoreUntil",0L);set(a,"sampleNeedsQuiet",false);set(a,"capturing",true);
      detected.invoke(a,"Em");detected.invoke(a,"Em");assertEquals(1,get(a,"score"));
      call(a,"cancelGame");set(a,"experience",0);set(a,"phase","running");set(a,"step",3);set(a,"theme","light");call(a,"render");shot(a,"four-voicings-top");
      android.widget.ScrollView scroll=(android.widget.ScrollView)get(a,"mainScroll");scroll.scrollTo(0,500);shot(a,"four-voicings-bottom");
      Object root=get(a,"root");int scrollY=scroll.getScrollY();call(a,"render");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertSame(root,get(a,"root"));assertEquals(scrollY,((android.widget.ScrollView)get(a,"mainScroll")).getScrollY());
    } finally {ctl.pause().stop().destroy();}
  }
  @Test
  public void bottomNavigationSightSpeedBonusAndPause() throws Exception {
    shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
    try{
      shot(a,"bottom-menu");click(a,"По схеме");click(a,"На скорость");assertEquals(true,get(a,"sightTimed"));shot(a,"sight-speed-menu");
      click(a,"Играть на время");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100));assertEquals("running",get(a,"phase"));assertEquals(false,get(a,"playing"));
      Music.Position target=(Music.Position)get(a,"target");assertNotNull(find(a.getWindow().getDecorView(),Music.label(target.midi(),true)));shot(a,"sight-speed-active");
      Method detected=MainActivity.class.getDeclaredMethod("onDetected",String.class);detected.setAccessible(true);
      for(int i=0;i<3;i++){
        set(a,"ignoreUntil",0L);set(a,"sampleNeedsQuiet",false);set(a,"capturing",true);target=(Music.Position)get(a,"target");detected.invoke(a,String.valueOf(target.midi()));detected.invoke(a,String.valueOf(target.midi()));assertEquals(i+1,get(a,"score"));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
      }
      assertEquals(5000L,get(a,"bonusTotal"));assertEquals(3,get(a,"best"));click(a,"Ⅱ");assertEquals("paused",get(a,"phase"));assertEquals(false,get(a,"capturing"));long remaining=(Long)get(a,"remaining");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));assertEquals(remaining,get(a,"remaining"));shot(a,"sight-speed-paused");
      click(a,"Продолжить");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100));assertEquals("running",get(a,"phase"));set(a,"ignoreUntil",0L);set(a,"sampleNeedsQuiet",false);set(a,"capturing",true);set(a,"deadline",android.os.SystemClock.elapsedRealtime()-1);target=(Music.Position)get(a,"target");detected.invoke(a,String.valueOf(target.midi()));assertEquals("result",get(a,"phase"));assertEquals(3,get(a,"score"));assertEquals(false,get(a,"capturing"));shot(a,"sight-speed-result");
      click(a,"На звук");click(a,"Обучение");click(a,"Начать");Object panel=get(a,"learnAnswers"),meter=get(a,"micMeter");call(a,"chooseName");assertSame(panel,get(a,"learnAnswers"));assertSame(meter,get(a,"micMeter"));shot(a,"unified-learning");
    }finally{ctl.pause().stop().destroy();}
  }
  @Test public void sampleMutesOnlyFirstSecondAndBarreWorks() throws Exception {
    shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
    try {
      click(a,"Начать");set(a,"capturing",true);
      long start=android.os.SystemClock.elapsedRealtime();assertEquals(1000L,(Long)get(a,"ignoreUntil")-start);
      Method detected=MainActivity.class.getDeclaredMethod("onDetected",String.class);detected.setAccessible(true);
      Music.Position target=(Music.Position)get(a,"target");String key=String.valueOf(target.midi()%12);
      detected.invoke(a,key);detected.invoke(a,key);assertEquals(2,get(a,"step"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));set(a,"capturing",true);assertEquals(false,get(a,"playing"));set(a,"micRms",0f);detected.invoke(a,(Object)null);detected.invoke(a,(Object)null);
      detected.invoke(a,key);detected.invoke(a,key);assertEquals(3,get(a,"step"));
      click(a,"По схеме");click(a,"Аккорды");click(a,"С баррэ");assertEquals(1,get(a,"sightChordSet"));shot(a,"barre-menu");click(a,"Начать");
      assertNotNull(Music.barre((Music.Chord)get(a,"sightChord")));
      set(a,"chordTarget",8);set(a,"sightChord",Music.voicings(Music.CHORDS[8])[0]);call(a,"render");shot(a,"barre-f");
      Method play=MainActivity.class.getDeclaredMethod("playSample",boolean.class);play.setAccessible(true);play.invoke(a,true);set(a,"capturing",true);
      start=android.os.SystemClock.elapsedRealtime();assertEquals(1000L,(Long)get(a,"ignoreUntil")-start);
      detected.invoke(a,"F");detected.invoke(a,"F");assertEquals(false,get(a,"answered"));
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));set(a,"capturing",true);assertEquals(false,get(a,"playing"));set(a,"micRms",0f);detected.invoke(a,(Object)null);detected.invoke(a,(Object)null);
      detected.invoke(a,"F");detected.invoke(a,"F");assertEquals(true,get(a,"answered"));assertEquals(true,get(a,"answerCorrect"));
    } finally {ctl.pause().stop().destroy();}
  }

  @Test public void sightChordFiltersUseMatchingShapesAndSeparateRecords() throws Exception {
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup(); MainActivity a=ctl.get();
    try {
      click(a,"По схеме"); click(a,"Аккорды");
      Method key=MainActivity.class.getDeclaredMethod("recordKey"); key.setAccessible(true);
      click(a,"Открытые"); String open=(String)key.invoke(a);
      for(int i=0;i<80;i++){call(a,"pickTarget"); assertTrue((Integer)get(a,"chordTarget")<8); assertNull(Music.barre((Music.Chord)get(a,"sightChord")));}
      click(a,"С баррэ"); String barre=(String)key.invoke(a);
      for(int i=0;i<80;i++){call(a,"pickTarget"); assertNotNull(Music.barre((Music.Chord)get(a,"sightChord")));}
      click(a,"Все"); String all=(String)key.invoke(a);
      assertNotEquals(open,barre); assertNotEquals(open,all); assertNotEquals(barre,all);
    } finally {ctl.pause().stop().destroy();}
  }

  @Test public void earBarreOneAttemptIconsAndConfetti() throws Exception {
    Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1);
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
    try {
      Button bottom=(Button)find(a.getWindow().getDecorView(),"Без гитары");assertNotNull(bottom.getCompoundDrawables()[1]);click(a,"Без гитары");click(a,"Аккорды");click(a,"С баррэ");assertEquals(1,get(a,"earChordSet"));click(a,"На скорость");shot(a,"ear-barre-menu");click(a,"Начать игру");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100));
      Music.Chord[] shapes=(Music.Chord[])get(a,"earShapes");for(Music.Chord c:shapes)assertNotNull(Music.barre(c));shot(a,"ear-barre-speed");
      int target=(Integer)get(a,"chordTarget"),wrong=0;int[] options=(int[])get(a,"options");while(options[wrong]==target)wrong++;
      Method guess=MainActivity.class.getDeclaredMethod("guessChord",int.class);guess.setAccessible(true);set(a,"streak",2);guess.invoke(a,wrong);assertEquals(true,get(a,"answered"));assertEquals(0,get(a,"streak"));assertEquals(0,get(a,"score"));
      int correct=0;while(options[correct]!=target)correct++;guess.invoke(a,correct);assertEquals(0,get(a,"score"));shot(a,"ear-one-attempt-wrong");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));assertEquals(false,get(a,"answered"));assertNotEquals(target,get(a,"chordTarget"));
      options=(int[])get(a,"options");target=(Integer)get(a,"chordTarget");correct=0;while(options[correct]!=target)correct++;set(a,"streak",2);long before=(Long)get(a,"deadline");guess.invoke(a,correct);assertEquals(5000L,(Long)get(a,"deadline")-before);assertEquals(1,get(a,"score"));assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("success-confetti"));shot(a,"ear-confetti");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300));assertNull(a.findViewById(android.R.id.content).findViewWithTag("success-confetti"));
    } finally {ctl.pause().stop().destroy();}
  }

  @Test public void recordsAndPolishedToolbar() throws Exception {
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
    try {
      android.content.SharedPreferences prefs=(android.content.SharedPreferences)get(a,"prefs");prefs.edit().clear().putInt("record-1-1-false-false-5",7).apply();
      Method key=MainActivity.class.getDeclaredMethod("recordKey");key.setAccessible(true);Method record=MainActivity.class.getDeclaredMethod("currentRecord");record.setAccessible(true);
      set(a,"experience",1);assertEquals(7,record.invoke(a));String sound=(String)key.invoke(a);
      set(a,"experience",3);set(a,"sightTimed",true);set(a,"chords",true);assertEquals(0,record.invoke(a));prefs.edit().putInt((String)key.invoke(a),11).apply();set(a,"sightChordSet",1);assertEquals(0,record.invoke(a));prefs.edit().putInt((String)key.invoke(a),4).apply();call(a,"render");shot(a,"records-barre-menu");
      set(a,"experience",2);set(a,"earTimed",true);set(a,"earChordSet",1);assertEquals(0,record.invoke(a));assertNotEquals(sound,key.invoke(a));set(a,"earChordSet",0);assertEquals(0,record.invoke(a));
      set(a,"experience",1);set(a,"chords",false);assertEquals(7,record.invoke(a));call(a,"render");shot(a,"records-sound-menu");set(a,"theme","dark");call(a,"render");shot(a,"navigation-dark");set(a,"theme","light");call(a,"render");
      call(a,"showQuickHelp");android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());View v=dialog.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(358,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(780,View.MeasureSpec.AT_MOST));v.layout(0,0,358,v.getMeasuredHeight());Bitmap bmp=Bitmap.createBitmap(358,v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(bmp));try(FileOutputStream out=new FileOutputStream("build/reports/native-ui/styled-help.png")){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();dialog.dismiss();
    } finally {ctl.pause().stop().destroy();}
  }

  @Test public void learningReturnAndNavigation() throws Exception {
    ActivityController<MainActivity> ctl=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=ctl.get();
    try {set(a,"permissionAsked",true);click(a,"Начать");assertEquals("running",get(a,"phase"));shot(a,"learning-toolbar");int score=(Integer)get(a,"score");click(a,"Меню");assertEquals("menu",get(a,"phase"));assertEquals(score,get(a,"score"));assertEquals(false,get(a,"capturing"));shot(a,"nav-repaired-light");set(a,"theme","dark");call(a,"render");shot(a,"nav-repaired-dark");}finally{ctl.pause().stop().destroy();}
  }

}
