package app.aleatorika;

import android.animation.*;
import android.app.Activity;
import android.graphics.*;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;
import java.util.Random;

/** Lightweight, non-interactive success particles; no audio or gameplay timers. */
public final class ConfettiView extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[][] particles=new float[54][6];
  private final int[] colors={0xff2783DE,0xff72BC8F,0xffEAC26B,0xffBF8EDA,0xff5E9FE8};
  private ValueAnimator animator;private float progress;
  private ConfettiView(Activity a){super(a);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);Random r=new Random();for(int i=0;i<particles.length;i++)particles[i]=new float[]{i%2==0?.18f:.82f,(i%2==0?1:-1)*(.18f+r.nextFloat()*.42f),-.28f-r.nextFloat()*.32f,4+r.nextFloat()*4,r.nextFloat()*360,(r.nextFloat()-.5f)*800};}
  public static void show(Activity a){
    if(a.isFinishing()||Settings.Global.getFloat(a.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)==0)return;
    FrameLayout host=a.findViewById(android.R.id.content);if(host==null)return;
    View old=host.findViewWithTag("success-confetti");if(old!=null)host.removeView(old);
    ConfettiView view=new ConfettiView(a);view.setTag("success-confetti");host.addView(view,new FrameLayout.LayoutParams(-1,-1));
    view.animator=ValueAnimator.ofFloat(0,1);view.animator.setDuration(1150);view.animator.setInterpolator(new android.view.animation.LinearInterpolator());view.animator.addUpdateListener(v->{view.progress=(Float)v.getAnimatedValue();view.invalidate();});view.animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){if(view.getParent()==host)host.removeView(view);}});view.animator.start();
  }
  @Override protected void onDraw(Canvas c){float t=progress*1.15f,w=getWidth(),h=getHeight(),density=getResources().getDisplayMetrics().density;for(int i=0;i<particles.length;i++){float[] p=particles[i];float x=(p[0]+p[1]*t)*w,y=(.48f+p[2]*t+.38f*t*t)*h,s=p[3]*density;paint.setColor(colors[i%colors.length]);paint.setAlpha((int)(255*Math.min(1,(1.15f-t)/.35f)));c.save();c.translate(x,y);c.rotate(p[4]+p[5]*t);c.drawRoundRect(-s/2,-s/3,s/2,s/3,s*.15f,s*.15f,paint);c.restore();}}
  @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(animator!=null)animator.cancel();}
}
