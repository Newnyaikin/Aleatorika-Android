package app.aleatorika;

import java.util.HashSet;
import java.util.Set;

/** Exactly one result per note; repeated taps on a known wrong position are free. */
public final class AttemptRound {
  public enum Result {
    RETRY,
    CORRECT,
    EXHAUSTED,
    IGNORED
  }

  public final int limit;
  public int used = 0;
  public boolean resolved = false;
  public final Set<Integer> wrong = new HashSet<>();

  public AttemptRound(int limit) {
    this.limit = limit;
  }

  public int left() {
    return Math.max(0, limit - used);
  }

  public Result guess(int position, boolean correct) {
    if (resolved || wrong.contains(position)) return Result.IGNORED;
    used++;
    if (correct) {
      resolved = true;
      return Result.CORRECT;
    }
    wrong.add(position);
    if (used >= limit) {
      resolved = true;
      return Result.EXHAUSTED;
    }
    return Result.RETRY;
  }
}
