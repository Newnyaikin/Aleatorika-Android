package app.aleatorika;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Native Android UI. No WebView, JavaScript bridge, remote page or server. */
public final class MainActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Random random = new Random();
  private AudioEngine audio;
  private android.content.SharedPreferences prefs;
  private LinearLayout root, body;
  private TextView timeView, scoreView, comboView, micView, focusMeta;
  private Button replay, goPlay;
  private MicVisualizerView micMeter;
  private ScrollView fretScrollView, mainScroll;
  private int cachedInsetTop=0,cachedInsetBottom=0,cachedInsetLeft=0,cachedInsetRight=0;
  private String renderedScreen = "";
  private Music.Chord sightChord,earChord;
  private Music.Chord[] earShapes=new Music.Chord[3];
  private int earChordSet=0;
  private boolean sightTimed=false;
  private int sightChordSet=2; // 0 open, 1 barre, 2 all
  private boolean sampleNeedsQuiet=false;private int sampleQuietFrames=0;
  private LinearLayout learnAnswers,learnMicPanel;
  private Button learnManual;
  private int fretScroll = 0;
  private float[] micEnvelope = new float[17];
  private float micRms = 0, micPeak = 0;
  private long lastMeterUi = 0;
  private int captureEpoch = 0, captureRetries = 0;
  private boolean permissionAsked = false;
  private int bg, surface, soft, ink, muted, line, accent, green, greenBg, red, redBg;
  private boolean dark;
  private Typeface appFont,appBold;
  private String theme = "system",
      phase = "menu",
      status = "",
      lastStable = null,
      lastWrong = null,
      lastAccepted = null;
  private int experience = 0,
      step = 1,
      range = 5,
      round = 0,
      score = 0,
      streak = 0,
      best = 0,
      errors = 0,
      chordTarget = 0,
      selected = -1,
      countdown = 3,
      stableFrames = 0,
      flash = 0;
  private boolean chords = false,
      earTimed = false,
      focusMode = false,
      playing = false,
      answered = false,
      answerCorrect = false,
      matched = false,
      capturing = false;
  private long startMs = 30000,
      bonusMs = 5000,
      remaining = 30000,
      deadline = 0,
      bonusTotal = 0,
      ignoreUntil = 0,
      lockUntil = 0;
  private int comboEvery = 3, attemptLimit = 3, totalRounds = 10;
  private int gameToken = 0, questionSerial = 0;
  private Music.Position target;
  private int[] options = new int[3];
  private AttemptRound attempts = new AttemptRound(3);
  private final List<Boolean> results = new ArrayList<>();
  private Runnable permissionAction;
  private final Runnable clock =
      new Runnable() {
        public void run() {
          if (phase.equals("running") && timed()) {
            remaining = Math.max(0, deadline - SystemClock.elapsedRealtime());
            updateHud();
            if (remaining == 0) {
              finishGame();
              return;
            }
          }
          if (phase.equals("running") && timed()) handler.postDelayed(this, 100);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    appFont = getResources().getFont(R.font.app_manrope);
    appBold = getResources().getFont(R.font.brand_manrope);
    prefs = getSharedPreferences("aleatorika-native", MODE_PRIVATE);
    audio = new AudioEngine();
    theme = prefs.getString("theme", "system");
    Music.setFlats(prefs.getBoolean("flats", false));
    range = prefs.getInt("range", 5);
    sightChordSet=prefs.getInt("sight-chord-set", prefs.getBoolean("sight-barre-only",false) ? 1 : 2);
    if(sightChordSet<0 || sightChordSet>2) sightChordSet=2;
    earChordSet=prefs.getInt("ear-chord-set",0);
    try {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      try (java.io.InputStream input = getAssets().open("rules.json")) {
        byte[] buffer = new byte[2048];
        int count;
        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
      }
      String json = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      JSONObject r = new JSONObject(json);
      startMs = r.getLong("startTimeMs");
      bonusMs = r.getLong("comboBonusMs");
      comboEvery = r.getInt("comboEvery");
      attemptLimit = r.getInt("noteAttempts");
      totalRounds = r.getInt("practiceRounds");
    } catch (Exception ignored) {
    }
    remaining = startMs;
    render();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private boolean timed() {
    return experience == 1 || (experience == 2 && earTimed) || (experience == 3 && sightTimed);
  }

  private boolean noGuitar() {
    return experience == 2;
  }

  private void colors() {
    dark =
        theme.equals("dark")
            || (theme.equals("system")
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
    bg = c(dark ? "#181E25" : "#F7F8FA");
    surface = c(dark ? "#202831" : "#FFFFFF");
    soft = c(dark ? "#2A333E" : "#F0F3F6");
    ink = c(dark ? "#FFFFFF" : "#2C2C2B");
    muted = c(dark ? "#B0BBC6" : "#66717B");
    line = c(dark ? "#35414C" : "#E7EBEF");
    accent = c(dark ? "#95BDE5" : "#3979A9");
    green = c(dark ? "#8BD7A6" : "#25764B");
    greenBg = c(dark ? "#24392C" : "#E8F1EC");
    red = c(dark ? "#FFB0A6" : "#B7352B");
    redBg = c(dark ? "#422925" : "#FCE9E7");
  }

  private static int c(String v) {
    return Color.parseColor(v);
  }

  private GradientDrawable shape(int color, int border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(16));
    if (border != 0) d.setStroke(dp(1), border);
    return d;
  }

  private GradientDrawable gradient(int start,int end,int border,int radius){
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
    d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;
  }
  private GradientDrawable canvasBackground(){return gradient(bg,c(dark?"#222939":"#EFF2FA"),0,0);}
  private GradientDrawable panelBackground(){return gradient(surface,c(dark?"#252E3C":"#F7FAFE"),line,18);}
  private GradientDrawable primaryBackground(){return gradient(c(dark?"#9EC6E9":"#397BA9"),c(dark?"#B8B3E9":"#526FB2"),0,16);}
  private ImageButton iconButton(int kind,String label,Runnable action){
    ImageButton b=new ImageButton(this);b.setImageDrawable(new ButtonIcon(kind,ink,dp(23)));b.setContentDescription(label);
    b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(10),dp(10),dp(10),dp(10));
    b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226C92BC),gradient(surface,soft,line,14),null));
    b.setOnClickListener(v->action.run());return b;
  }
  private TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(2), 1);
    t.setTypeface(bold?appBold:appFont);
    t.setFontFeatureSettings("tnum");
    return t;
  }

  private void gap(LinearLayout into, int n) {
    View v = new View(this);
    into.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
  }

  private void para(LinearLayout into, String value) {
    if(!value.equals(status)&&!value.equals("попаданий")&&!value.startsWith("Личный рекорд"))return;
    TextView t = text(value, 16, muted, false);
    into.addView(t);
    gap(into, 16);
  }

  private void heading(LinearLayout into, String value) {
    if(value.equals("Ноты на гитаре"))value="Ноты";
    else if(value.equals("Аккорды на гитаре"))value="Аккорды";
    else if(value.startsWith("Услышь и повтори"))value="Слушай и играй";
    else if(value.equals("Играй по схеме")||value.equals("По схеме на скорость"))value="По схеме";
    else if(value.equals("Аккорды по рисунку"))value="Узнай аккорд";
    else if(value.equals("Ноты на грифе"))value="Найди ноту";
    TextView t = text(value, 25, ink, true);
    into.addView(t);
    gap(into, 12);
  }

  private Button button(String title, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setIncludeFontPadding(false);
    b.setTypeface(appBold);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(50));
    b.setMinimumHeight(dp(50));
    b.setTextColor(primary ? (dark ? c("#122A40") : Color.WHITE) : ink);
    android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(dark ? 0x3380BAFF : 0x22176CB5),
        primary ? primaryBackground() : shape(surface,line), null);
    b.setBackground(ripple);
    b.setPadding(dp(12), dp(8), dp(12), dp(8));
    int icon=ButtonIcon.forLabel(title);if(icon!=0){b.setCompoundDrawables(new ButtonIcon(icon,primary?(dark?c("#122A40"):Color.WHITE):ink,dp(18)),null,null,null);b.setCompoundDrawablePadding(dp(7));}
    b.setStateListAnimator(null);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private Button addButton(LinearLayout into, String title, boolean primary, Runnable action) {
    Button b = button(title, primary, action);
    into.addView(b, new LinearLayout.LayoutParams(-1, -2));
    gap(into, 12);
    return b;
  }

  private LinearLayout row() {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    return r;
  }

  private LinearLayout card(LinearLayout into) {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setPadding(dp(20), dp(22), dp(20), dp(22));
    v.setBackground(panelBackground());
    into.addView(v, new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  private void nav(
      LinearLayout into, String[] names, int active, java.util.function.IntConsumer change) {
    LinearLayout r = row();
    if(names.length > 3) into.addView(r);
    for (int i = 0; i < names.length; i++) {
      if(names.length > 3 && i == 2){gap(into,6); r=row(); into.addView(r);}
      final int index = i;
      Button b = button(names[i], false, () -> change.accept(index));
      b.setTextSize(14);
      b.setTextColor(i == active ? accent : muted);
      b.setBackground(
          shape(i == active ? (dark ? c("#233143") : c("#E5F2FC")) : bg, i == active ? accent : 0));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
      if (i > 0 && !(names.length > 3 && i == 2)) lp.leftMargin = dp(6);
      r.addView(b, lp);
    }
    if(names.length <= 3) into.addView(r);
    gap(into, 16);
  }

  private void render() {
    final int previousScroll = mainScroll == null ? 0 : mainScroll.getScrollY();
    final String screen = experience + ":" + chords + ":" + phase;
    final boolean sameScreen = screen.equals(renderedScreen);
    renderedScreen = screen;
    if (fretScrollView != null) fretScroll = fretScrollView.getScrollY();
    fretScrollView = null;
    colors();
    boolean full = focusMode && noGuitar() && !chords && phase.equals("running");
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    int flags =
        dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    // Keep system insets stable when switching pages and focus mode.
    getWindow().getDecorView().setSystemUiVisibility(flags);
    boolean firstRoot = root == null;
    if(firstRoot) root = new LinearLayout(this);
    else root.removeAllViews();
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackground(canvasBackground());
    root.setPadding(dp(full ? 12 : 20)+cachedInsetLeft, dp(full ? 12 : 20)+cachedInsetTop, dp(full ? 12 : 20)+cachedInsetRight, dp(full ? 16 : 20)+cachedInsetBottom);
    if(firstRoot) setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          int top = insets.getSystemWindowInsetTop(),
              bottom = insets.getSystemWindowInsetBottom(),
              left = insets.getSystemWindowInsetLeft(),
              right = insets.getSystemWindowInsetRight();
          if (full && Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
            top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
            bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
          }
          cachedInsetTop=top;cachedInsetBottom=bottom;cachedInsetLeft=left;cachedInsetRight=right;
          root.setPadding(
              dp(full ? 12 : 20) + left,
              dp(full ? 12 : 20) + top,
              dp(full ? 12 : 20) + right,
              dp(full ? 16 : 20) + bottom);
          return insets;
        });
    root.requestApplyInsets();
    timeView = null;
    scoreView = null;
    comboView = null;
    focusMeta = null;
    micView = null;
    micMeter = null;
    replay = null;
    goPlay = null;
    if (full) {
      renderFocus();
      addBottomNavigation();
      return;
    }
    ScrollView scroll = new ScrollView(this);
    mainScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setFocusableInTouchMode(true);
    scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
    boolean running=phase.equals("running");
    learnAnswers=null;learnManual=null;
    if(running) {
      if(experience==0) renderLearningToolbar();
    } else {
      renderHeader();
      if(!phase.equals("countdown"))addSetupControls();
    }
    switch (phase) {
      case "menu" -> renderMenu();
      case "countdown" -> renderCountdown();
      case "running" -> {
        if (experience == 3) renderSight();
        else if (noGuitar()) renderEar();
        else if (timed()) renderPhysicalSpeed();
        else renderLearn();
      }
      case "paused" -> renderPaused();
      case "result" -> renderResult();
      default -> renderMenu();
    }
    addBottomNavigation();
    final ScrollView activeScroll = scroll;
    scroll.post(() -> activeScroll.scrollTo(0, sameScreen ? previousScroll : 0));
    if (!sameScreen) {
      try {
        float scale =
            Settings.Global.getFloat(
                getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        if (scale > 0) {
          body.setAlpha(.3f);
          body.setTranslationY(0);
          body.animate().alpha(1).translationY(0).setDuration(220).start();
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void addBottomNavigation(){
    LinearLayout bar=row();bar.setPadding(0,dp(8),0,0);bar.setBackgroundColor(Color.TRANSPARENT);
    String[] labels={"На звук","По схеме","Без гитары"};
    int category=experience==3?1:experience==2?2:0;
    for(int i=0;i<3;i++){
      final int index=i;boolean selected=i==category;int color=selected?accent:muted;
      Button tab=button(labels[i],false,()->{
        cancelGame();experience=index==0?0:index==1?3:2;phase="menu";render();
      });
      tab.setTextSize(13);tab.setSingleLine(true);tab.setPadding(dp(4),dp(4),dp(4),dp(4));
      tab.setCompoundDrawables(null,new ButtonIcon(i==0?9:i==1?2:1,color,dp(24)),null,null);tab.setCompoundDrawablePadding(dp(4));tab.setTextColor(color);
      int fill=selected?c(dark?"#293C50":"#E5F2FC"):surface;
      tab.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x228DBDE5:0x123979A9),shape(fill,0),null));
      tab.setSelected(selected);tab.setContentDescription(labels[i]+(selected?", выбран":""));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);if(i>0)lp.leftMargin=dp(6);bar.addView(tab,lp);
    }
    root.addView(bar,new LinearLayout.LayoutParams(-1,dp(64)));
  }

  private void addSetupControls(){
    LinearLayout material=row(),pace=row();
    
    
    LinearLayout notes=new LinearLayout(this),tempos=new LinearLayout(this);notes.setOrientation(LinearLayout.VERTICAL);tempos.setOrientation(LinearLayout.VERTICAL);
    addSegment(notes,new String[]{"Ноты","Аккорды"},chords?1:0,i->{cancelGame();chords=i==1;phase="menu";render();});
    addSegment(tempos,new String[]{"Обучение","На скорость"},timed()?1:0,i->{cancelGame();if(experience==3)sightTimed=i==1;else if(experience==2)earTimed=i==1;else experience=i;phase="menu";render();});
    material.addView(notes,new LinearLayout.LayoutParams(0,-2,1));pace.addView(tempos,new LinearLayout.LayoutParams(0,-2,1));body.addView(material);gap(body,10);body.addView(pace);gap(body,16);
  }

  private void addSegment(LinearLayout into,String[] labels,int active,java.util.function.IntConsumer change){
    LinearLayout r=row();r.setPadding(dp(3),dp(3),dp(3),dp(3));r.setBackground(shape(soft,0));
    for(int i=0;i<labels.length;i++){final int index=i;Button b=button(labels[i],false,()->change.accept(index));b.setTextSize(14);b.setPadding(dp(2),dp(4),dp(2),dp(4));if(b.getCompoundDrawables()[0]!=null){b.setCompoundDrawables(null,b.getCompoundDrawables()[0],null,null);b.setCompoundDrawablePadding(dp(2));}b.setTextColor(i==active?ink:muted);b.setBackground(shape(i==active?surface:soft,0));r.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}into.addView(r);
  }

  private void renderHeader() {
    LinearLayout header = row();
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_launcher);
    header.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));
    TextView title = text("алеаторика", getResources().getConfiguration().screenWidthDp<360?17:21, ink, true);
    title.setSingleLine(true);
    title.setLetterSpacing(-0.035f);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = dp(8);lp.rightMargin=dp(8);
    header.addView(title, lp);
    header.addView(iconButton(10,"Как играть",this::showQuickHelp),new LinearLayout.LayoutParams(dp(44),dp(44)));
    LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(44),dp(44));settingsLp.leftMargin=dp(8);
    header.addView(iconButton(11,"Настройки",this::settings),settingsLp);
    body.addView(header);
    gap(body, 16);
  }

  private void showQuickHelp(){
    if(phase.equals("running")||phase.equals("countdown"))pauseGame();
    String intro=noGuitar()?"Послушай звук и выбери рисунок. В аккордах можно выбрать открытые позиции, баррэ или все формы. На скорость — одна попытка на аккорд. В нотах — три попытки.":experience==3?"Повтори схему на гитаре. Для нот важна указанная октава. Проверяем звучание аккорда, а не пальцы.":"Послушай короткий образец. Когда появится «Играй», повтори на гитаре. Затем выбери название. «Без проверки» — ручное продолжение.";
    Dialog dialog=new Dialog(this);
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(24),dp(24),dp(24),dp(24));panel.setBackground(panelBackground());
    LinearLayout top=row();TextView title=text("Как играть",24,ink,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    top.addView(iconButton(13,"Закрыть справку",dialog::dismiss),new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(top);gap(panel,12);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
    String[] paragraphs={intro,"Образец не засчитывается: после короткого звука проверка ждёт защитную секунду и тихий промежуток. После «Играй» нужен новый звук гитары. Наушники уменьшают эхо.","На скорость: 30 секунд, +5 секунд за каждые три попадания подряд. Рекорды сохраняются отдельно для каждого режима.","× — не играть; ○ — открытая струна; полоска — баррэ. Число слева — первый показанный лад. Струны: от 6-й слева до 1-й справа.","Строй E–A–D–G–B–E. Звук обрабатывается только на устройстве."};
    for(String paragraph:paragraphs){TextView t=text(paragraph,15,muted,false);t.setLineSpacing(dp(3),1.1f);copy.addView(t);gap(copy,16);}
    scroll.addView(copy);int available=dp(getResources().getConfiguration().screenHeightDp)-dp(240);copy.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels-dp(80),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
    panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(copy.getMeasuredHeight(),Math.max(dp(120),Math.min(dp(430),available)))));gap(panel,16);
    panel.addView(button("Понятно",true,dialog::dismiss),new LinearLayout.LayoutParams(-1,dp(50)));dialog.setContentView(panel);
    Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.3f;window.setAttributes(params);}
    dialog.show();if(window!=null)window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(520)),-2);

  }

  private void renderMenu() {
    if(experience == 3){renderSightMenu();return;}
    LinearLayout box = card(body);
    heading(
        box,
        noGuitar()
            ? (chords ? "Аккорды по рисунку" : "Ноты на грифе")
            : timed() ? "Игра на скорость" : chords ? "Аккорды на гитаре" : "Ноты на гитаре");
    para(
        box,
        noGuitar()
            ? (chords
                ? "Послушай аккорд и выбери его аппликатуру. Название появится после ответа."
                : "Послушай ноту и отметь её положение на грифе. На каждую ноту — 3 попытки. Нужна"
                    + " та же октава.")
            : timed()
                ? "Услышь образец и повтори на гитаре. Название выбирать не нужно."
                : "Послушай образец, повтори на гитаре и выбери название. Новые задания звучат"
                    + " автоматически.");
    if (noGuitar() && !chords) {
      box.addView(text("Диапазон ладов", 14, ink, true));
      gap(box, 8);
      nav(
          box,
          new String[] {"0–5", "0–12", "0–19", "0–24"},
          range == 24 ? 3 : range == 19 ? 2 : range == 12 ? 1 : 0,
          i -> {
            range = new int[] {5, 12, 19, 24}[i];
            prefs.edit().putInt("range", range).apply();
            render();
          });
    }
    if(noGuitar()&&chords){
      box.addView(text("Аппликатуры",14,ink,true));gap(box,8);
      nav(box,new String[]{"Открытые","С баррэ","Все"},earChordSet,i->{earChordSet=i;prefs.edit().putInt("ear-chord-set",i).apply();render();});
      para(box,timed()?"Одна попытка на аккорд. Ошибка сбрасывает комбо; затем — следующий звук.":"Можно выбирать открытые аккорды или формы с баррэ, включая F, Fm, B, Bm, B♭ и B♭m.");
    }
    if (timed()) {
      LinearLayout rules = row();
      stat(rules, "3–2–1", "до старта");
      stat(rules, "30 сек", "на старте");
      stat(rules, "+5 сек", "за 3 подряд");

      gap(box, 20);
      para(
          box,
          "Верный ответ — следующий звук. Ошибка сбрасывает комбо. Игра продолжается, пока остаётся"
              + " время.");
      if (chords && !noGuitar())
        para(box, "Автораспознавание 8 открытых аккордов — бета. Лучше использовать наушники.");
    } else para(box, totalRounds + " заданий. Можно переслушивать звук без ограничения.");
    if (!status.isEmpty()) {
      box.addView(text(status, 14, red, false));
      gap(box, 14);
    }
    if(timed())addModeRecord(box);
    addButton(box, timed() ? "Начать игру" : "Начать", true, () -> startGame(false));
    if (!noGuitar())
      para(
          box,
          "Для проверки гитары нужен микрофон. В обучении есть кнопка без проверки. Используй"
              + " наушники.");
    else para(box, "Микрофон и гитара не нужны. Строй: E–A–D–G–B–E.");
  }

  private void stat(LinearLayout row, String number, String label) {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setPadding(dp(8), dp(12), dp(8), dp(12));
    v.setBackground(shape(soft, line));
    v.addView(text(number, 21, ink, true));
    v.addView(text(label, 14, muted, false));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.rightMargin = dp(6);
    row.addView(v, lp);
  }

  private boolean hasMic() {
    return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void withMic(Runnable action) {
    if (hasMic()) {
      action.run();
      return;
    }
    permissionAsked = true;
    permissionAction = action;
    requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 12);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request != 12) return;
    Runnable action = permissionAction;
    permissionAction = null;
    if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
      if (action != null) action.run();
    } else {
      status =
          "Нет доступа к микрофону. Разреши его в настройках Android. В обучении можно продолжить"
              + " без проверки.";
      capturing = false;
      if(experience == 3){phase="menu";status="Для игры по схеме нужен доступ к микрофону.";render();}
      else if (!timed() && action != null) action.run();
      else render();
    }
  }

  private void startGame(boolean resume) {
    if (!noGuitar() && !hasMic() && (!permissionAsked || timed() || experience == 3)) {
      withMic(() -> startGame(resume));
      return;
    }
    stopCapture();
    audio.stopSound();
    playing = false;
    handler.removeCallbacks(clock);
    gameToken++;
    captureRetries = 0;
    status = "";
    flash = 0;
    if (!resume) {
      answered = false;
      matched = false;
      round = 0;
      score = 0;
      streak = 0;
      best = 0;
      errors = 0;
      bonusTotal = 0;
      results.clear();
      remaining = startMs;
      target = null;
      lastWrong = null;
      lastAccepted = null;
    }
    int token = gameToken;
    if (timed()) {
      phase = "countdown";
      countdown = 3;
      render();
      Runnable countdownTask =
          new Runnable() {
            public void run() {
              if (token != gameToken || !phase.equals("countdown")) return;
              countdown--;
              if (countdown <= 0) {
                beginRunning(resume);
                return;
              }
              render();
              handler.postDelayed(this, 1000);
            }
          };
      handler.postDelayed(countdownTask, 1000);
    } else beginRunning(resume);
  }

  private void beginRunning(boolean resume) {
    phase = "running";
    if (!resume || target == null) pickTarget();
    focusMode = noGuitar() && !chords;
    if (timed()) {
      deadline = SystemClock.elapsedRealtime() + remaining;
      handler.removeCallbacks(clock);
      handler.post(clock);
    }
    if (!noGuitar()) startCapture();
    render();
    if (!answered) { if (experience != 3) playSample(false); }
    else if (!timed()) advanceLater(1000);
  }

  private void pickTarget() {
    fretScrollView = null;
    fretScroll = 0;
    int previous = target == null ? -1 : target.midi() % 12;
    if (chords) {
      int old = chordTarget;
      int next;
      do next = random.nextInt((experience==3&&sightChordSet!=0)||(experience==2&&earChordSet>0)?Music.CHORDS.length:8);
      while (next == old);
      chordTarget = next;
      List<Music.Chord> shapes=new ArrayList<>();
      if(experience==3 && sightChordSet==0) shapes.add(Music.CHORDS[next]);
      else for(Music.Chord shape:Music.voicings(Music.CHORDS[next])) if(experience!=3 || sightChordSet!=1 || Music.barre(shape)!=null) shapes.add(shape);
      sightChord=shapes.get(random.nextInt(shapes.size()));
      target = new Music.Position(6, 0, Music.CHORDS[next].midis()[0]);
      List<Integer> pool = new ArrayList<>();
      for (int i = 0; i < (experience==2&&earChordSet>0?Music.CHORDS.length:8); i++) if (i != next) pool.add(i);
      Collections.shuffle(pool, random);
      List<Integer> choices = new ArrayList<>(Arrays.asList(next, pool.get(0), pool.get(1)));
      Collections.shuffle(choices, random);
      for (int i = 0; i < 3; i++) options[i] = choices.get(i);
      if(experience==2){earChord=earShape(chordTarget);for(int i=0;i<3;i++)earShapes[i]=options[i]==chordTarget?earChord:earShape(options[i]);}
    } else {
      target = Music.pick(random, noGuitar() ? range : 5, previous);
      List<Integer> pool = new ArrayList<>();
      for (int pc : Music.CHROMATIC) if (pc != target.midi() % 12) pool.add(pc);
      Collections.shuffle(pool, random);
      List<Integer> choices =
          new ArrayList<>(Arrays.asList(target.midi() % 12, pool.get(0), pool.get(1)));
      Collections.shuffle(choices, random);
      for (int i = 0; i < 3; i++) options[i] = choices.get(i);
    }
    attempts = new AttemptRound(attemptLimit);
    answered = false;
    answerCorrect = false;
    matched = false;
    selected = -1;
    status = "";
    lockUntil = 0;
    step = noGuitar() || timed() ? 1 : 2;
    questionSerial++;
    lastStable = null;
    stableFrames = 0;
    lastWrong = null;
  }

  private Music.Chord earShape(int index){
    Music.Chord c=Music.CHORDS[index];if(earChordSet==0)return c;
    List<Music.Chord> shapes=new ArrayList<>();for(Music.Chord v:Music.voicings(c))if(earChordSet!=1||Music.barre(v)!=null)shapes.add(v);
    return shapes.get(random.nextInt(shapes.size()));
  }

  private void nextQuestion() {
    audio.stopSound();
    playing = false;
    if (!timed()) {
      if (round >= totalRounds - 1) {
        finishGame();
        return;
      }
      round++;
    }
    pickTarget();
    render();
    if(experience != 3) playSample(false);
  }

  private void playSample(boolean repeat) {
    if (!phase.equals("running") || playing) return;
    if (!noGuitar()) startCapture();
    int[] mids = chords ? (experience == 3 ? sightChord : experience==2 ? earChord : Music.CHORDS[chordTarget]).midis() : new int[] {target.midi()};
    int duration = audio.play(mids,!noGuitar());
    if(!noGuitar()){sampleNeedsQuiet=true;sampleQuietFrames=0;}
    playing = true;
    if(micView!=null)micView.setText("Образец…");
    // Do not stop capture: suppress recognition only for the first second.
    ignoreUntil = SystemClock.elapsedRealtime() + 1000;
    lastStable = null;
    stableFrames = 0;
    int serial = questionSerial, token = gameToken;
    if (replay != null) {
      replay.setText("Звучит образец…");
      replay.setEnabled(false);
    }
    if (goPlay != null) goPlay.setEnabled(false);
    handler.postDelayed(
        () -> {
          if (serial != questionSerial || token != gameToken || !phase.equals("running")) return;
          playing = false;
          if (!noGuitar()) startCapture();
          if (micView != null && sampleNeedsQuiet&&!noGuitar())micView.setText("Жду тишины…");
          else if (micView != null && step == 2)
            micView.setText(
                step==3&&experience==0?"Образец прозвучал. Выбери название ниже.":capturing
                    ? "Играй"
                    : "Нет доступа к микрофону. Можно продолжить без проверки.");
          if (replay != null) {
            replay.setText("Образец");
            replay.setEnabled(!answered || !timed());
          }
          if (goPlay != null) goPlay.setEnabled(true);
        },
        duration);
  }

  private void addReplay(LinearLayout into) {
    replay =
        addButton(
            into,
            playing ? "Звучит образец…" : "Образец",
            true,
            () -> playSample(true));
    replay.setEnabled(!playing && !answered);
  }

  private void renderCountdown() {
    LinearLayout b = card(body);
    TextView title = text("ПРИГОТОВЬСЯ", 14, accent, true);
    title.setGravity(Gravity.CENTER);
    b.addView(title);
    gap(b, 24);
    TextView number = text(String.valueOf(countdown), 90, accent, true);
    number.setGravity(Gravity.CENTER);
    b.addView(number);
    gap(b, 24);
    para(b, "После отсчёта образец прозвучит автоматически.");
    addButton(
        b,
        "Отмена",
        false,
        () -> {
          cancelGame();
          phase = "menu";
          render();
        });
  }

  private void learningSteps(LinearLayout b) {
    LinearLayout r = row();
    String[] labels = {step == 3 ? "✓ Слушай и играй" : "1 Слушай и играй", "2 Узнавай"};
    for (int i = 0; i < 2; i++) {
      boolean active = (i == 0 && step <= 2) || (i == 1 && step == 3);
      TextView t = text(labels[i], 14, active ? accent : muted, active);
      r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    }
    b.addView(r);
    gap(b, 20);
  }

  private void renderLearningToolbar(){
    LinearLayout tools=row();Button menu=button("Меню",false,()->{cancelGame();phase="menu";render();});menu.setTextSize(14);menu.setPadding(dp(8),dp(6),dp(8),dp(6));tools.addView(menu,new LinearLayout.LayoutParams(dp(92),dp(44)));
    TextView label=text("Обучение",13,muted,false);label.setGravity(Gravity.CENTER);tools.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    tools.addView(iconButton(10,"Как играть",this::showQuickHelp),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(44),dp(44));settingsLp.leftMargin=dp(8);tools.addView(iconButton(11,"Настройки",this::settings),settingsLp);body.addView(tools);gap(body,12);
  }

  private void renderLearn() {
    LinearLayout b=card(body);
    b.addView(text("Обучение · "+(round+1)+" / "+totalRounds,14,accent,true));gap(b,12);
    heading(b,chords?"Услышь и повтори аккорд":"Услышь и повтори ноту");
    para(b,"Послушай образец и повтори на гитаре. Всё происходит здесь — без переключения экрана.");
    addReplay(b);
    LinearLayout panel=new LinearLayout(this);learnMicPanel=panel;panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(8),dp(12),dp(12));panel.setBackground(shape(step==3?greenBg:soft,0));addMeter(panel);
    micView=text(step==3?"Верно":playing?"Слушай образец…":capturing?"Играй":"Микрофон недоступен. Можно продолжить без проверки.",15,step==3?green:muted,false);micView.setGravity(Gravity.CENTER);panel.addView(micView);b.addView(panel);gap(b,12);
    learnManual=addButton(b,"Без проверки",false,this::chooseName);learnManual.setVisibility(step==3?View.GONE:View.VISIBLE);
    learnAnswers=new LinearLayout(this);learnAnswers.setOrientation(LinearLayout.VERTICAL);b.addView(learnAnswers);if(step==3)populateLearningAnswers();
    gap(body,12);para(body,"Используй наушники, чтобы микрофон слышал только гитару.");
  }

  private void populateLearningAnswers(){
    if(learnAnswers==null)return;LinearLayout b=learnAnswers;b.removeAllViews();
    b.addView(text(chords?"Выбери аккорд":"Выбери ноту",18,ink,true));gap(b,12);
    LinearLayout choices=row();
    for(int i=0;i<3;i++){
      final int index=i;String name=chords?Music.CHORDS[options[i]].name():Music.RU[options[i]]+" / "+Music.EN[options[i]];
      Button option=button(name,false,()->guessName(index));option.setTextSize(17);option.setEnabled(!answered);
      boolean correct=chords?options[i]==chordTarget:options[i]==target.midi()%12;
      if(answered&&(correct||selected==i)){option.setTextColor(correct?green:red);option.setBackground(shape(correct?greenBg:redBg,correct?green:red));}
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(96),1);if(i>0)lp.leftMargin=dp(8);choices.addView(option,lp);
    }
    b.addView(choices);gap(b,16);if(chords)addVoicings(b,Music.CHORDS[chordTarget]);
    if(answered){showFeedback(b);para(b,"Следующее задание через секунду…");}
  }

  private void chooseName() {
    if(matched)ConfettiView.show(this);
    audio.stopSound();
    playing = false;
    step = 3;
    lastStable = null;
    stableFrames = 0;
    if(learnAnswers!=null){
      if(learnManual!=null)learnManual.setVisibility(View.GONE);
      if(micView!=null){micView.setText("Верно");micView.setTextColor(green);}
      if(learnMicPanel!=null)learnMicPanel.setBackground(shape(greenBg,0));
      populateLearningAnswers();
      try{float scale=Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1);if(scale>0){learnAnswers.setAlpha(0);learnAnswers.animate().alpha(1).setDuration(450).start();}}catch(Exception ignored){}
    }else render();
  }

  private void guessName(int index) {
    if (answered) return;
    audio.stopSound();
    playing = false;
    selected = index;
    resolvePractice(chords ? options[index] == chordTarget : options[index] == target.midi() % 12);
    if(answerCorrect)ConfettiView.show(this);
    render();
  }

  private void resolvePractice(boolean correct) {
    answered = true;
    answerCorrect = correct;
    results.add(correct);
    advanceLater(1000);
    if (correct) score++;
    prefs
        .edit()
        .putInt("total", prefs.getInt("total", 0) + 1)
        .putInt("correct", prefs.getInt("correct", 0) + (correct ? 1 : 0))
        .apply();
  }

  private String answerName() {
    return chords ? Music.CHORDS[chordTarget].name() : Music.label(target.midi(), true);
  }

  private void showFeedback(LinearLayout b) {
    TextView t =
        text(
            (answerCorrect ? "✓ Верно!" : "Ответ показан")
                + "\n"
                + answerName()
                + (chords ? "" : " · " + Music.octave(target.midi())),
            16,
            answerCorrect ? green : red,
            false);
    t.setPadding(dp(14), dp(14), dp(14), dp(14));
    t.setBackground(shape(answerCorrect ? greenBg : redBg, 0));
    b.addView(t);
    gap(b, 16);
    if (chords) {
      if(experience != 0) addVoicings(b, Music.CHORDS[chordTarget]);
    } else
      para(
          b,
          target.string()
              + "-я струна, "
              + (target.fret() == 0 ? "открытая" : target.fret() + "-й лад")
              + ". Позиция соответствует прозвучавшей октаве.");
  }

  private void addVoicings(LinearLayout into, Music.Chord chord) {
    para(into,"Один аккорд · четыре позиции");
    Music.Chord[] shapes=Music.voicings(chord);
    String[] labels={"Открытая","Форма E","Форма A","Форма D"};
    for(int rowIndex=0;rowIndex<2;rowIndex++){
      LinearLayout r=row();
      for(int j=0;j<2;j++){
        int i=rowIndex*2+j;
        LinearLayout cell=new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER); cell.setPadding(dp(8),dp(12),dp(8),dp(8));
        cell.setBackground(shape(soft,line));
        ChordView diagram=new ChordView(this,shapes[i],dark);
        cell.addView(diagram,new LinearLayout.LayoutParams(dp(105),dp(132)));
        TextView label=text(labels[i],14,muted,false); cell.addView(label);
        cell.setContentDescription(labels[i]+", "+fretsDescription(shapes[i]));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); if(j>0)lp.leftMargin=dp(8); r.addView(cell,lp);
      }
      into.addView(r); gap(into,8);
    }
    para(into,"× — не играть · ○ — открытая. Число слева — первый показанный лад. Формы E и A — с баррэ.");
  }

  private void renderSightMenu(){
    LinearLayout b=card(body);
    b.addView(text("СМОТРИ → ПОВТОРЯЙ",14,accent,true));gap(b,12);
    heading(b,timed()?"По схеме на скорость":"Играй по схеме");
    para(b,"Название "+(chords?"аккорда":"ноты")+" видно сразу. Просто повтори рисунок на гитаре — без угадывания названия.");
    LinearLayout facts=row();stat(facts,timed()?"30 сек":"10 схем",timed()?"на старте":"без спешки");stat(facts,timed()?"+5 сек":"Микрофон",timed()?"за 3 попадания":"проверяет игру");gap(b,8);
    if(chords){
      b.addView(text("Аппликатуры",14,ink,true));gap(b,8);
      nav(b,new String[]{"Открытые","С баррэ","Все"},sightChordSet,i->{sightChordSet=i;prefs.edit().putInt("sight-chord-set",i).apply();render();});
    }
    if(!status.isEmpty())para(b,status);
    if(timed())addModeRecord(b);
    addButton(b,timed()?"Играть на время":"Начать",true,()->startGame(false));
    para(b,"Образец не звучит автоматически. Смотри на схему и сразу играй.");
    if(chords)para(b,"Открытые и баррэ: F, Fm, B, Bm, B♭ и B♭m включены. Полоска на схеме — баррэ. Проверяется звучание, не положение пальцев.");
  }

  private void renderSight(){
    LinearLayout top=row();
    Button back=button("Итоги",false,this::finishGame);back.setTextSize(14);top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(44)));
    TextView caption=text("По схеме · "+(timed()?"на время":"обучение"),14,muted,false);caption.setGravity(Gravity.CENTER);top.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
    ImageButton help=iconButton(10,"Как играть",this::showQuickHelp);top.addView(help,new LinearLayout.LayoutParams(dp(44),dp(44)));
    Button pause=button("Ⅱ",false,this::pauseGame);pause.setContentDescription("Пауза");LinearLayout.LayoutParams pauseLp=new LinearLayout.LayoutParams(dp(44),dp(44));pauseLp.leftMargin=dp(8);top.addView(pause,pauseLp);body.addView(top);gap(body,12);
    LinearLayout b=card(body);b.setPadding(dp(16),dp(16),dp(16),dp(16));hud(b);
    TextView prompt=text("По схеме",14,muted,false);prompt.setGravity(Gravity.CENTER);prompt.setVisibility(View.GONE);b.addView(prompt);gap(b,8);
    TextView title=text(chords?Music.CHORDS[chordTarget].name():Music.label(target.midi(),true),27,ink,true);title.setGravity(Gravity.CENTER);b.addView(title);gap(b,8);
    Music.Chord diagramChord=sightChord;
    if(chords&&Music.barre(diagramChord)!=null){TextView position=text("Баррэ · "+Music.barre(diagramChord)[0]+" лад",14,muted,false);position.setGravity(Gravity.CENTER);b.addView(position);}
    if(!chords){
      int[] frets={-1,-1,-1,-1,-1,-1};frets[6-target.string()]=target.fret();diagramChord=new Music.Chord("нота",target.midi()%12,false,frets);
      TextView position=text(target.string()+"-я струна · "+(target.fret()==0?"открытая":target.fret()+" лад"),14,muted,false);position.setGravity(Gravity.CENTER);b.addView(position);
    }
    ChordView diagram=new ChordView(this,diagramChord,dark);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(144),dp(166));lp.gravity=Gravity.CENTER;b.addView(diagram,lp);diagram.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);diagram.setContentDescription(fretsDescription(diagramChord));
    TextView legend=text("× не играть · ○ открытая · струны 6 → 1",14,muted,false);legend.setGravity(Gravity.CENTER);legend.setVisibility(View.GONE);b.addView(legend);
    micMeter=new MicVisualizerView(this,accent,muted,red);micMeter.update(micEnvelope,micRms,micPeak,capturing);b.addView(micMeter,new LinearLayout.LayoutParams(-1,dp(64)));
    micView=text(answered?status:capturing?"Играй":status.isEmpty()?"Микрофон не подключён":status,15,answered&&answerCorrect?green:muted,answered);micView.setGravity(Gravity.CENTER);b.addView(micView);gap(b,12);
    LinearLayout actions=row();replay=button("♫ Образец",false,()->playSample(true));replay.setEnabled(!playing&&!answered);replay.setTextSize(15);actions.addView(replay,new LinearLayout.LayoutParams(0,dp(48),1));
    Button skip=button("Пропустить",false,this::skipSight);skip.setTextSize(15);skip.setEnabled(!answered);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(48),1);slp.leftMargin=dp(10);actions.addView(skip,slp);b.addView(actions);
    if(!capturing&&!answered){gap(b,8);addButton(b,"Повторить микрофон",true,()->{status="";captureRetries=0;startCapture();render();});}
    gap(b,10);TextView note=text(timed()?"3 попадания подряд → +5 секунд":chords?"Проверяем звучание, не положение пальцев.":"Играй в указанной октаве.",14,muted,false);note.setGravity(Gravity.CENTER);b.addView(note);
  }

  private void skipSight(){
    if(answered||!phase.equals("running"))return;
    if(timed()){
      if(SystemClock.elapsedRealtime()>=deadline){finishGame();return;}
      answered=true;answerCorrect=false;streak=0;errors++;status="Задание пропущено";audio.stopSound();playing=false;ignoreUntil=Long.MAX_VALUE;render();advanceLater(180);
    }else{resolvePractice(false);status="Задание пропущено";render();}
  }

  private void hud(LinearLayout into) {
    LinearLayout row = row();
    LinearLayout a = new LinearLayout(this);
    a.setOrientation(LinearLayout.VERTICAL);
    a.addView(text(timed() ? "Осталось" : "Задание", 14, muted, false));
    timeView =
        text(timed() ? seconds() : String.valueOf(round + 1) + " / " + totalRounds, 26, ink, true);
    a.addView(timeView);
    row.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout b = new LinearLayout(this);
    b.setOrientation(LinearLayout.VERTICAL);
    b.addView(text("Попадания", 14, muted, false));
    scoreView = text(String.valueOf(score), 26, ink, true);
    b.addView(scoreView);
    row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    if (timed()) {
      LinearLayout d = new LinearLayout(this);
      d.setOrientation(LinearLayout.VERTICAL);
      d.addView(text("Комбо", 14, muted, false));
      comboView = text((streak % 3 == 0 && streak > 0 ? 3 : streak % 3) + " / 3", 26, accent, true);
      d.addView(comboView);
      row.addView(d, new LinearLayout.LayoutParams(0, -2, 1));
    }
    into.addView(row);
    gap(into, 20);
  }

  private String seconds() {
    return String.valueOf((remaining + 999) / 1000);
  }

  private String attemptText() {
    return answered
        ? (answerCorrect ? "Угадано · попытка " + attempts.used + " / 3" : "Попытки закончились")
        : "Попытки: " + attempts.left() + " / 3";
  }

  private void updateHud() {
    if (timeView != null && timed()) {
      timeView.setText(seconds());
      timeView.setTextColor(remaining < 10000 ? red : ink);
    }
    if (scoreView != null) scoreView.setText(String.valueOf(score));
    if (comboView != null)
      comboView.setText((streak % 3 == 0 && streak > 0 ? 3 : streak % 3) + " / 3");
    if (focusMeta != null)
      focusMeta.setText(
          (timed()
                  ? seconds() + " сек · " + score + " попаданий"
                  : (round + 1) + " / " + totalRounds)
              + "\n"
              + attemptText());
  }

  private FretView fret() {
    return new FretView(
        this, range, target.midi(), answered, dark, answered, attempts.wrong, this::guessPosition);
  }

  private void renderFocus() {
    LinearLayout bar = row();
    Button back =
        button(
            "← Назад",
            false,
            () -> {
              focusMode = false;
              render();
            });
    bar.addView(back, new LinearLayout.LayoutParams(dp(104), dp(46)));
    focusMeta = text("", 14, muted, false);
    focusMeta.setGravity(Gravity.END);
    bar.addView(focusMeta, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton help=iconButton(10,"Как играть",this::showQuickHelp);bar.addView(help,new LinearLayout.LayoutParams(dp(44),dp(44)));
    root.addView(bar);
    gap(root, 12);
    ScrollView view = new ScrollView(this);
    view.setFillViewport(true);
    view.setBackground(shape(surface, line));
    FretView fret = fret();
    view.addView(fret, new ScrollView.LayoutParams(-1, -2));
    view.setOnScrollChangeListener((v, x, y, oldx, oldy) -> fret.invalidate());
    restoreFretScroll(view, fret);
    root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
    gap(root, 12);
    if (!status.isEmpty()) {
      TextView t =
          text(
              status,
              14,
              answerCorrect ? green : errors > 0 || !attempts.wrong.isEmpty() ? red : muted,
              false);
      root.addView(t);
      gap(root, 10);
    }
    if (answered && !timed()) {
      Button next = button("Следующее задание…", true, () -> {});
      next.setEnabled(false);
      root.addView(next, new LinearLayout.LayoutParams(-1, dp(52)));
    } else {
      replay =
          button(
              playing
                  ? "Звучит образец…"
                  : answered ? "Следующая нота…" : "Образец",
              true,
              () -> playSample(true));
      replay.setEnabled(!playing && !answered);
      root.addView(replay, new LinearLayout.LayoutParams(-1, dp(52)));
    }
    updateHud();
  }

  private void restoreFretScroll(ScrollView view, FretView fret) {
    fretScrollView = view;
    final int saved = fretScroll;
    view.post(
        () -> {
          if (view != fretScrollView) return;
          view.scrollTo(0, answered && !answerCorrect ? fret.answerScroll() : saved);
        });
  }

  private void addFocusAction(String text, Runnable action) {
    root.addView(button(text, true, action), new LinearLayout.LayoutParams(-1, dp(52)));
  }

  private void renderSessionToolbar(String label){
    LinearLayout top=row();
    Button back=button("Итоги",false,this::finishGame); back.setTextSize(14);
    top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(44)));
    TextView caption=text(label,13,muted,false); caption.setGravity(Gravity.CENTER);
    top.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
    Button pause=button("Ⅱ",false,this::pauseGame); pause.setContentDescription("Пауза");
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(44)); p.leftMargin=dp(8); top.addView(pause,p);
    body.addView(top); gap(body,12);
  }

  private void renderEar() {
    renderSessionToolbar("Без гитары · " + (timed()?"на время":"практика"));
    LinearLayout b = card(body);
    hud(b);
    heading(b, chords ? "Какой рисунок звучит?" : "Где прозвучала нота?");
    if (!chords) {
      b.addView(text(attemptText(), 14, muted, false));
      gap(b, 12);
    }
    addReplay(b);
    if (chords) {
      para(b, "6-я струна слева, 1-я справа. ○ — открытая, × — не играть.");
      LinearLayout choices = row();
      for (int i = 0; i < 3; i++) {
        final int index = i;
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        boolean correct = options[i] == chordTarget;
        int border = answered && correct ? green : selected == i && !answerCorrect ? red : line;
        v.setBackground(shape(answered && correct ? greenBg : surface, border));
        TextView n = text(String.valueOf(i + 1), 14, muted, true);
        n.setGravity(Gravity.CENTER);
        v.addView(n);
        ChordView diagram = new ChordView(this, earShapes[i], dark);
        v.addView(diagram, new LinearLayout.LayoutParams(-1, dp(130)));
        v.setContentDescription(
            "Рисунок " + (i + 1) + ". " + fretsDescription(earShapes[i]));
        v.setFocusable(true);
        v.setClickable(!answered);
        v.setOnClickListener(w -> guessChord(index));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (i > 0) lp.leftMargin = dp(8);
        choices.addView(v, lp);
      }
      b.addView(choices);
      gap(b, 16);
    } else {
      addButton(
          b,
          "Гриф на весь экран",
          false,
          () -> {
            focusMode = true;
            render();
          });
      ScrollView view = new ScrollView(this);
      FretView f = fret();
      view.addView(f, new ScrollView.LayoutParams(-1, -2));
      view.setOnScrollChangeListener((v, x, y, ox, oy) -> f.invalidate());
      restoreFretScroll(view, f);
      b.addView(view, new LinearLayout.LayoutParams(-1, dp(380)));
      gap(b, 16);
    }
    if (!status.isEmpty()) {
      b.addView(
          text(status, 14, answerCorrect ? green : attempts.wrong.isEmpty() ? muted : red, false));
      gap(b, 12);
    }
    if (answered && !timed()) para(b, "Следующее задание через секунду…");

  }

  private String fretsDescription(Music.Chord c) {
    StringBuilder b = new StringBuilder("От 6-й струны к 1-й: ");
    for (int f : c.frets)
      b.append(f < 0 ? "не играть" : f == 0 ? "открытая" : f + " лад").append(", ");
    return b.toString();
  }

  private void guessPosition(int string, int fret) {
    if (answered || !phase.equals("running") || SystemClock.elapsedRealtime() < lockUntil) return;
    boolean correct = Music.OPEN[6 - string] + fret == target.midi();
    AttemptRound.Result verdict = attempts.guess(string * 100 + fret, correct);
    if (verdict == AttemptRound.Result.IGNORED) return;
    audio.stopSound();
    playing = false;
    lockUntil = SystemClock.elapsedRealtime() + 450;
    if (correct) {
      answerCorrect = true;
      status = "✓ Верно! " + answerName();
      if (timed()) speedHit();
      else {
        resolvePractice(true);
        render();
        flash(true);
      }
    } else {
      errors++;
      if (timed()) streak = 0;
      if (verdict == AttemptRound.Result.RETRY) {
        status =
            "Не совпало. " + (attempts.left() == 1 ? "Осталась 1 попытка." : "Осталось 2 попытки.");
        render();
        flash(false);
      } else {
        answerCorrect = false;
        status = "Попытки закончились. Ответ: " + answerName() + ".";
        if (timed()) {
          answered = true;
          render();
          flash(false);
          advanceLater(1100);
        } else {
          resolvePractice(false);
          render();
          flash(false);
        }
      }
    }
  }

  private void guessChord(int index) {
    if (answered || SystemClock.elapsedRealtime() < lockUntil || !phase.equals("running")) return;
    if(timed()&&SystemClock.elapsedRealtime()>=deadline){finishGame();return;}
    selected = index;
    boolean correct = options[index] == chordTarget;
    if (timed()) {
      if (correct) {
        answerCorrect = true;
        speedHit();
      } else {
        streak = 0;
        errors++;
        answered=true;answerCorrect=false;audio.stopSound();playing=false;
        status="Неверно. Ответ: "+answerName()+". Следующий аккорд…";
        ignoreUntil=Long.MAX_VALUE;render();flash(false);advanceLater(850);
      }
    } else {
      audio.stopSound();
      playing = false;
      resolvePractice(correct);
      status = (correct ? "✓ Верно! " : "Правильный ответ: ") + answerName();
      render();
      flash(correct);
    }
  }

  private void speedHit() {
    if(SystemClock.elapsedRealtime()>=deadline){finishGame();return;}
    score++;
    streak++;
    best = Math.max(best, streak);
    answered = true;
    answerCorrect = true;
    audio.stopSound();
    playing = false;
    boolean bonus = streak % comboEvery == 0;
    if (bonus) {
      deadline += bonusMs;
      bonusTotal += bonusMs;
    }
    status = bonus ? "Комбо из 3! +5 секунд" : experience==3?"✓ Верно! Следующая схема.":"✓ Верно! Следующий звук.";
    remaining = Math.max(0, deadline - SystemClock.elapsedRealtime());
    ignoreUntil = Long.MAX_VALUE;
    render();
    flash(true);
    advanceLater(300);
  }

  private void advanceLater(int delay) {
    int token = gameToken, serial = questionSerial;
    handler.postDelayed(
        () -> {
          if (token != gameToken || serial != questionSerial || !phase.equals("running")) return;
          if (timed() && SystemClock.elapsedRealtime() >= deadline) {
            finishGame();
            return;
          }
          nextQuestion();
        },
        delay);
  }

  private void renderPhysicalSpeed() {
    renderSessionToolbar("На звук · на время");
    LinearLayout b = card(body);
    hud(b);
    heading(b, answered ? "Попадание!" : "Повтори на гитаре");
    para(b, status.isEmpty() ? "Слушаю. Название выбирать не нужно." : status);
    gap(b, 30);
    addMeter(b);
    gap(b, 40);
    addReplay(b);
    replay.setEnabled(!playing && !answered);

    if (chords) para(b, "Распознавание открытых аккордов · бета");
  }

  private void addMeter(LinearLayout into) {
    micMeter = new MicVisualizerView(this, accent, muted, red);
    micMeter.update(micEnvelope, micRms, micPeak, capturing);
    into.addView(micMeter, new LinearLayout.LayoutParams(-1, dp(106)));
  }

  private void startCapture() {
    if (capturing || noGuitar() || !hasMic() || !phase.equals("running")) return;
    capturing = true;
    final int epoch = ++captureEpoch;
    lastMeterUi = 0;
    lastStable = null;
    stableFrames = 0;
    audio.listen(
        chords,
        experience == 3,
        new AudioEngine.Listener() {
          public void level(float[] envelope, float rms, float peak) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastMeterUi < 40) return;
            lastMeterUi = now;
            handler.post(
                () -> {
                  if (!capturing || epoch != captureEpoch) return;
                  captureRetries = 0;
                  micEnvelope = envelope;
                  micRms = rms;
                  micPeak = peak;
                  if (micMeter != null) micMeter.update(sampleNeedsQuiet?new float[17]:envelope, sampleNeedsQuiet?0:rms, sampleNeedsQuiet?0:peak, true);
                });
          }

          public void sample(String key) {
            handler.post(
                () -> {
                  if (capturing && epoch == captureEpoch) onDetected(key);
                });
          }

          public void error(String message) {
            handler.post(
                () -> {
                  if (epoch != captureEpoch) return;
                  stopCapture();
                  status = message;
                  if (phase.equals("running") && !noGuitar() && hasMic() && captureRetries++ < 2) {
                    final int token = gameToken;
                    handler.postDelayed(
                        () -> {
                          if (token == gameToken && phase.equals("running")) {
                            startCapture();
                            render();
                          }
                        },
                        600);
                  } else if (timed() && phase.equals("running")) pauseGame();
                  render();
                });
          }
        });
  }

  private void stopCapture() {
    captureEpoch++;
    capturing = false;
    micRms = 0;
    micPeak = 0;
    micEnvelope = new float[17];
    if (micMeter != null) micMeter.update(micEnvelope, 0, 0, false);
    if (audio != null) audio.stopMic();
    lastStable = null;
    stableFrames = 0;
  }

  private void onDetected(String key) {
    if (!capturing || !phase.equals("running") || SystemClock.elapsedRealtime() < ignoreUntil)
      return;
    if(timed()&&deadline>0&&SystemClock.elapsedRealtime()>=deadline){finishGame();return;}
    if(sampleNeedsQuiet){
      sampleQuietFrames=key==null&&micRms<.004f?sampleQuietFrames+1:0;
      if(sampleQuietFrames>=2){sampleNeedsQuiet=false;if(micView!=null)micView.setText("Играй");}
      lastStable=null;stableFrames=0;return;
    }
    if (key == null) {
      lastStable = null;
      stableFrames = 0;
      lastWrong = null;
      lastAccepted = null;
      return;
    }
    if (key.equals(lastStable)) stableFrames++;
    else {
      lastStable = key;
      stableFrames = 1;
    }
    if (stableFrames < 2) return;
    String expected = chords ? Music.CHORDS[chordTarget].id : String.valueOf(experience == 3 ? target.midi() : target.midi() % 12);
    if(experience == 3){
      if(answered) return;
      if(key.equals(expected)){matched=true;if(timed()){speedHit();}else{resolvePractice(true);status="✓ Верно! Следующая схема…";render();flash(true);}}
      else {if(timed()&&!key.equals(lastWrong)){lastWrong=key;streak=0;errors++;updateHud();}if(micView!=null)micView.setText("Пока не совпало. Повтори рисунок.");}
      return;
    }
    if (!timed()) {
      if (step != 2) return;
      if (key.equals(expected)) {
        matched = true;
        chooseName();
      } else if (micView != null)
        micView.setText("Пока не совпало. Сыграй ещё раз или продолжи без проверки.");
      return;
    }
    if (answered) return;
    if (key.equals(expected)) {
      lastAccepted = key;
      speedHit();
    } else if (!key.equals(lastWrong) && !key.equals(lastAccepted)) {
      lastWrong = key;
      streak = 0;
      errors++;
      status = "Не совпало. Попробуй ещё раз — звук тот же.";
      render();
      flash(false);
    }
  }

  private void flash(boolean correct) {
    if(correct)ConfettiView.show(this);
    flash = correct ? 1 : -1;
    root.setBackground(canvasBackground());
    if (!correct) {
      try {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator())
          v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
      } catch (Exception ignored) {
      }
    }
    int token = gameToken;
    handler.postDelayed(
        () -> {
          if (token != gameToken) return;
          flash = 0;
          if (root != null) root.setBackground(canvasBackground());
        },
        650);
  }

  private void pauseGame() {
    if (!phase.equals("running") && !phase.equals("countdown")) return;
    if (timed() && phase.equals("running"))
      remaining = Math.max(0, deadline - SystemClock.elapsedRealtime());
    if (timed() && answered) pickTarget();
    stopCapture();
    audio.stopSound();
    playing = false;
    handler.removeCallbacks(clock);
    gameToken++;
    focusMode = false;
    flash = 0;
    phase = "paused";
    render();
  }

  private void renderPaused() {
    LinearLayout b = card(body);
    heading(b, "Пауза");
    para(
        b,
        timed()
            ? "Осталось " + seconds() + " сек. Счёт и комбо сохранены."
            : "Задание и попытки сохранены.");
    addButton(b, "Продолжить", true, () -> startGame(true));
    addButton(b, "Завершить", false, this::finishGame);
  }

  private void cancelGame() {
    stopCapture();
    audio.stopSound();
    playing = false;
    handler.removeCallbacks(clock);
    gameToken++;
    questionSerial++;
    focusMode = false;
    flash = 0;
    status = "";
  }

  private String recordKey() {
    String key="record-2-"+(experience==3?"sight":experience==2?"ear":"sound")+"-"+(chords?"chords":"notes");
    if(experience==2)key+=chords?"-set-"+earChordSet:"-range-"+range;
    if(experience==3&&chords)key+=sightChordSet==1?"-barre":sightChordSet==0?"-open":"-all";
    return key;
  }
  private int currentRecord(){
    String key=recordKey();if(prefs.contains(key))return prefs.getInt(key,0);
    int value=0;
    // Older records had unrelated menu options in their keys. Keep them in the
    // corresponding default pool only; never copy an all-position score to barre.
    boolean migrate=!(experience==3&&chords&&sightChordSet!=2)&&!(experience==2&&chords&&earChordSet!=0);
    if(migrate)for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
      String[] parts=entry.getKey().split("-");if(parts.length<6||!parts[0].equals("record")||!parts[1].equals("1")||!(entry.getValue() instanceof Integer))continue;
      if(!parts[2].equals(String.valueOf(experience))||!parts[3].equals(String.valueOf(chords)))continue;
      if(experience==2&&(!parts[4].equals("true")||!chords&&!parts[5].equals(String.valueOf(range))))continue;
      if(experience==3&&(parts.length<8||!parts[7].equals("true")))continue;
      value=Math.max(value,(Integer)entry.getValue());
    }
    prefs.edit().putInt(key,value).apply();return value;
  }
  private void addModeRecord(LinearLayout into){
    LinearLayout record=row();record.setPadding(dp(14),dp(14),dp(14),dp(14));record.setBackground(gradient(soft,c(dark?"#2D3448":"#E9EFFA"),0,14));
    ImageView trophy=new ImageView(this);trophy.setImageDrawable(new ButtonIcon(12,accent,dp(20)));record.addView(trophy,new LinearLayout.LayoutParams(dp(20),dp(20)));
    TextView label=text("Рекорд",15,muted,false);LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(0,-2,1);labelLp.leftMargin=dp(10);record.addView(label,labelLp);
    record.addView(text(String.valueOf(currentRecord()),22,ink,true));into.addView(record,new LinearLayout.LayoutParams(-1,-2));gap(into,16);
  }

  private void finishGame() {
    cancelGame();
    phase = "result";
    if (timed())
      prefs.edit().putInt(recordKey(), Math.max(score, currentRecord())).apply();
    render();
  }

  private void renderResult() {
    LinearLayout b = card(body);
    heading(b, "Итоги тренировки");
    TextView large = text(score + (timed() ? "" : " / " + results.size()), 56, ink, true);
    b.addView(large);
    gap(b, 12);
    para(b, timed() ? "попаданий" : "правильных ответов");
    if (timed()) {
      LinearLayout first = row();
      stat(first, String.valueOf(best), "лучшее комбо");
      stat(first, "+" + formatMs(bonusTotal) + " сек", "бонус времени");
      b.addView(first);
      gap(b, 12);
      para(b, "Личный рекорд: " + currentRecord());
    }
    addButton(b, "Начать снова", true, () -> startGame(false));
    addButton(
        b,
        "К настройкам режима",
        false,
        () -> {
          phase = "menu";
          status = "";
          render();
        });
  }

  private String formatMs(long value) {
    return value % 1000 == 0
        ? String.valueOf(value / 1000)
        : String.format(new Locale("ru"), "%.1f", value / 1000.0);
  }

  private void settings() {
    if (timed() && (phase.equals("running") || phase.equals("countdown"))) pauseGame();
    else {
      gameToken++;
      audio.stopSound();
      playing = false;
      stopCapture();
    }
    LinearLayout b = new LinearLayout(this);
    b.setOrientation(LinearLayout.VERTICAL);
    b.setPadding(dp(24), dp(16), dp(24), dp(12));
    b.setBackground(panelBackground());
    TextView title = text("Тема приложения", 16, ink, true);
    b.addView(title);
    RadioGroup group = new RadioGroup(this);
    String[] titles = {"Как на устройстве", "Светлая", "Тёмная"},
        values = {"system", "light", "dark"};
    for (int i = 0; i < 3; i++) {
      RadioButton r = new RadioButton(this);
      r.setText(titles[i]);
      r.setTextColor(ink);
      r.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
      r.setTypeface(appFont);
      r.setId(100 + i);
      r.setMinHeight(dp(48));
      group.addView(r);
      if (theme.equals(values[i])) group.check(100 + i);
    }
    b.addView(group);
    gap(b, 16);
    b.addView(text("Знаки альтерации", 16, ink, true));
    RadioGroup accidentalGroup = new RadioGroup(this);
    String[] signs = {"♯ — диезы", "♭ — бемоли"};
    for (int i = 0; i < 2; i++) {
      RadioButton r = new RadioButton(this);
      r.setId(200 + i);
      r.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
      r.setTypeface(appFont);
      r.setText(signs[i]);
      r.setTextColor(ink);
      r.setMinHeight(dp(48));
      accidentalGroup.addView(r);
    }
    accidentalGroup.check(prefs.getBoolean("flats", false) ? 201 : 200);
    b.addView(accidentalGroup);
    gap(b, 8);
    para(b, "Все 12 нот. До♯ / C♯ и Ре♭ / D♭ — один звук. Меняется только написание.");
    gap(b, 16);
    para(
        b,
        "Ноты: До / C, Ре / D, Ми / E, Фа / F, Соль / G, Ля / A, Си / B.\n\n"
            + "Строй: E–A–D–G–B–E. A4 = 440 Гц.\n\n"
            + "Проверка 8 аккордов в разных позициях — бета. Аудио обрабатывается на"
            + " телефоне, не записывается в файлы и не отправляется.\n\n"
            + "Алеаторика 1.0");
    Dialog dialog = new Dialog(this);
    LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(20),dp(20),dp(20),dp(20)); panel.setBackground(panelBackground());
    LinearLayout top = row(); TextView dialogTitle=text("Настройки",24,ink,true);
    top.addView(dialogTitle,new LinearLayout.LayoutParams(0,-2,1));
    top.addView(iconButton(13,"Закрыть настройки",dialog::dismiss),new LinearLayout.LayoutParams(dp(44),dp(44)));
    panel.addView(top); gap(panel,12);
    ScrollView scroll = new ScrollView(this); scroll.addView(b);
    int available = Math.max(dp(120), Math.min(dp(430), dp(getResources().getConfiguration().screenHeightDp)-dp(240)));
    panel.addView(scroll,new LinearLayout.LayoutParams(-1,available)); gap(panel,16);
    panel.addView(button("Готово",true,()->{
      int id=group.getCheckedRadioButtonId()-100;
      theme=values[Math.max(0,Math.min(2,id))];
      boolean flats=accidentalGroup.getCheckedRadioButtonId()==201;
      Music.setFlats(flats); prefs.edit().putString("theme",theme).putBoolean("flats",flats).apply();
      dialog.dismiss();
    }),new LinearLayout.LayoutParams(-1,dp(50)));
    dialog.setContentView(panel);
    dialog.setOnDismissListener(
        d -> {
          render();
          if (phase.equals("running") && !noGuitar()) startCapture();
          if (phase.equals("running") && answered && !timed()) advanceLater(1000);
        });
    dialog.show();
    Window window=dialog.getWindow(); if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.3f;window.setAttributes(params);window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(520)),-2);}
  }

  @Override
  public void onBackPressed() {
    if (focusMode) {
      focusMode = false;
      render();
    } else if (phase.equals("running") || phase.equals("countdown")) pauseGame();
    else if (!phase.equals("menu")) {
      phase = "menu";
      status = "";
      render();
    } else super.onBackPressed();
  }

  @Override
  public void onConfigurationChanged(Configuration config) {
    super.onConfigurationChanged(config);
    render();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (phase.equals("running") || phase.equals("countdown")) pauseGame();
    else {
      audio.stopSound();
      stopCapture();
    }
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    audio.close();
    super.onDestroy();
  }
}
