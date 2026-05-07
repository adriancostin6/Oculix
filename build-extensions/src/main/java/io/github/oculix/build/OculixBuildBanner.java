/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package io.github.oculix.build;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Maven core extension that prints the OculiX brand banner once per mvn
 * invocation, on the {@code SessionStarted} lifecycle event, plus a status
 * footer (success / failure / wall-clock duration) on {@code SessionEnded}.
 *
 * <p>Loaded by Maven via {@code maven.ext.class.path} pointing at the
 * prebuilt jar in {@code .mvn/extensions/} — no Maven artifact resolution,
 * works from the very first clone.
 *
 * <p>All glyphs are pure 7-bit ASCII so legacy Windows cmd renders them
 * cleanly without UTF-8 emoji holes. ANSI color escape codes are handled
 * by JLine/Jansi which Maven 3.6+ ships with.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
@Named("oculix-build-banner")
@Singleton
public class OculixBuildBanner extends AbstractEventSpy {

  // Bare ESC-prefixed ANSI codes (the leading character is U+001B).
  // Encoded as actual bytes in the .java file so we don't pay any runtime
  // String.format / unicode escape cost on every build.
  private static final String RESET = "[0m";
  private static final String CYAN  = "[36m";
  private static final String BOLD  = "[1m";
  private static final String DIM   = "[2m";
  private static final String LIME  = "[32m";
  private static final String RED   = "[31m";
  private static final String AMBER = "[33m";

  /** Header banner printed at most once per JVM. */
  private static volatile boolean headerPrinted = false;
  /** Footer banner printed at most once per JVM. */
  private static volatile boolean footerPrinted = false;
  /** Wall-clock millis at SessionStarted, used by the footer to print duration. */
  private static volatile long startedAt = 0L;

  @Override
  public void onEvent(Object event) {
    if (!(event instanceof ExecutionEvent)) {
      return;
    }
    ExecutionEvent ee = (ExecutionEvent) event;
    try {
      switch (ee.getType()) {
        case SessionStarted:
          handleStart();
          break;
        case SessionEnded:
          handleEnd(ee);
          break;
        default:
          // ignore other lifecycle events (Mojo*, Project*, ForkStarted, ...)
      }
    } catch (Throwable t) {
      // Banner is decoration, never let it break the build.
      System.err.println("[oculix-banner] suppressed: " + t.getMessage());
    }
  }

  private void handleStart() {
    synchronized (OculixBuildBanner.class) {
      if (headerPrinted) return;
      headerPrinted = true;
      startedAt = System.currentTimeMillis();
    }
    StringBuilder out = new StringBuilder();
    out.append('\n');
    out.append(CYAN).append(GECKO).append(RESET).append('\n');
    out.append(BOLD).append(CYAN).append("  OculiX")
        .append(RESET).append(BOLD).append("  |  Visual Automation IDE")
        .append(RESET).append('\n');
    out.append(DIM).append("  visual automation, your way   ::   MIT licensed")
        .append(RESET).append('\n');
    out.append(DIM).append("  https://github.com/oculix-org/Oculix")
        .append(RESET).append('\n');
    out.append(LIME).append("  >>  preparing build...").append(RESET).append('\n');
    out.append('\n');
    System.out.println(out);
  }

  private void handleEnd(ExecutionEvent ee) {
    synchronized (OculixBuildBanner.class) {
      if (footerPrinted) return;
      footerPrinted = true;
    }
    long elapsedMs = startedAt > 0 ? System.currentTimeMillis() - startedAt : -1;
    String duration = elapsedMs < 0 ? "" : formatDuration(elapsedMs);
    boolean success = isSuccess(ee);

    StringBuilder out = new StringBuilder();
    out.append('\n');
    if (success) {
      out.append(LIME).append(BOLD).append("  (v)  Build green").append(RESET);
      if (!duration.isEmpty()) {
        out.append(DIM).append("  in ").append(duration).append(RESET);
      }
      out.append('\n');
      out.append(DIM).append("  ").append(pickLine(SUCCESS_TAGLINES))
          .append(RESET).append('\n');
    } else {
      out.append(RED).append(BOLD).append("  (x)  Build broken").append(RESET);
      if (!duration.isEmpty()) {
        out.append(DIM).append("  after ").append(duration).append(RESET);
      }
      out.append('\n');
      out.append(AMBER).append("  ").append(pickLine(FAILURE_TAGLINES))
          .append(RESET).append('\n');
    }
    out.append(DIM).append("  https://github.com/oculix-org/Oculix")
        .append(RESET).append('\n');
    System.out.println(out);
  }

  /**
   * Rotate through a small pool of taglines based on the current millis.
   * Deterministic-ish per run, varied across runs — gives the build a bit
   * of personality without being random-spam.
   */
  private static String pickLine(String[] pool) {
    if (pool == null || pool.length == 0) return "";
    int idx = (int) ((System.currentTimeMillis() / 7L) % pool.length);
    if (idx < 0) idx = -idx;
    return pool[idx % pool.length];
  }

  /** Fun one-liners on a green build — pro, terse, gecko-flavoured. */
  private static final String[] SUCCESS_TAGLINES = new String[] {
      "JAR sealed. gecko stamps approval.",
      "all green. tests didn't lie.",
      "compiled clean. cyan gecko approves.",
      "ship it before tomorrow's bug arrives.",
      "build done. coffee earned.",
      "no red, no drama. push when ready.",
      "the gecko has signed off. you may proceed.",
      "another OculiX build in the books.",
      "green light. capture some pixels.",
      "ship it. then take a walk."
  };

  /** Fun one-liners on a red build — sympathetic, never harsh. */
  private static final String[] FAILURE_TAGLINES = new String[] {
      "the gecko refuses this build. scroll up for clues.",
      "red light. the trace knows. you've got this.",
      "broken. take a breath. then check the logs.",
      "the JAR is unimpressed. logs above tell the story.",
      "scroll up. somewhere a gecko sighs.",
      "didn't pass the gecko. scroll up to debug.",
      "build refused. read the trace, fix the cause, retry.",
      "the cyan gecko did not stamp this one.",
      "red. the answer is in the logs above.",
      "not green yet. but it will be."
  };

  /**
   * True if the Maven session completed without exceptions. Defensive on
   * every access so a Maven internal API change won't crash the footer
   * (which would scream stderr while the actual build was fine).
   */
  private static boolean isSuccess(ExecutionEvent ee) {
    try {
      if (ee.getSession() != null && ee.getSession().getResult() != null) {
        return !ee.getSession().getResult().hasExceptions();
      }
    } catch (Throwable ignore) {
      // fall through to "assume success"
    }
    return true;
  }

  /** Concise human-friendly duration: "850 ms" / "12.3s" / "2m 5s". */
  private static String formatDuration(long ms) {
    if (ms < 1000) return ms + " ms";
    long s = ms / 1000;
    if (s < 60) return s + "." + ((ms % 1000) / 100) + "s";
    long m = s / 60;
    long sr = s % 60;
    return m + "m " + sr + "s";
  }

  /**
   * The OculiX gecko, hand-pixel'd in dense ASCII. Designed for monospace
   * terminals at 100-column width — renders as a recognisable gecko head +
   * body with the cyclope eye centred. If your terminal narrows past 100,
   * the gecko wraps and looks abstract — which is also fine, just less
   * sharp.
   */
  private static final String GECKO =
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%%%##*++===--==++*#%%@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%#*=----=+*#%%%%%@@@%%%%#*+=+*%%@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%*==+*#%%%%@@@@@@@@@@@@@@@@@@@@@%%#**#%@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@%%#+==+*%@@%@@+*#%%%@@@@@@@@@@@@@@@@@@#:::*@+=-*@@@@%%##@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@%%=..........-%%@@@@@@@%%@@@@@@@@@@@@@@@@*...*%----@@@@@@@@%%@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@%+....:-==-:....+%%@%=:....:*%-..*@@@%::-@*..:#@#++%@%%%%@@@@%%##%%@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@%=...:*#%%%:.:-...+%+...:--:.+%-..*@@@#...@+..:%%...*%::--%%%%*:...*%@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@#...:*#@@@@-.-*+...*...*%@@@@@%:..#@@@*..:@+..:%*...#@*:..:##:...:*%@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@+...=#@@@@@@@@#*...+...%@@@@@@@:..#@@@+..=@+..:@=..:%@@#:......:*%@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@*...-*#@@@@@@#*=...#...=#%%#+%@-..:#%+...+@+..:@-..=%@@@#.....+%@@@@@@%*+#@@@@@@@@@@\n" +
      "  @@@@@@@#==*@@@@@%-...-==*%%*+=-...+@#:.......:@#:.....:..*%=..=@-..=@@@*:....:#@@@@@%#%@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@#*%@@@%-....:----:....*@@@@@*=-=*%@@@@@%#%@@%%@@+-:*%-..=@#:...::..:#%@@@@%%#####@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@*:..........=#@@@@@@%%###**++======+***##%%@@@@%%*:..:#%#...:#%@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@#++=+*#@@@@@@@@%*+-:-=*#%@@%#***###%%#+*++=-----:-=+*%%###***#@@@@@@@@#....#%@@#*%@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@%@@@@@@@@@@@@@@#*%%@@%%##*=**=-=+*##*+-:.:-==-*@@@@@%%#*#%@@@@@#-.:#@@@@%+*@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@%**@@@@@@@@@@@@@%@@@@@@@*:=-+++*#@@@@@@@@@@@@*-:-===++-+#%@@@%%%%@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@++%@@@@@@@@@@@@@@@@@@@@%#++=+**%%#@@@@%%@%%+*%%@%=-========#@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@%%%@@@@@@@@=+##*+#%*+@@%%@@@@@+:..*@@@*====--==-+@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@%+=-:=%@@@@@%+##*#*%+=@%%%@@@@@@#-::+%@@@*==-=-++==+@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@#++=--%@@@@@%+###*%*-#%%#@@@@@@@@%@@@%%%#@+==+=++*+-#@@@@#+=%@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@%*===@%#*+=*@@@@@@@+=*##%--#%**@@@@@@@@@@@@##%###+==***++-#@@@%%@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@%#*+==+@@+==%@%+-=%%=+**##=-##+*%%@@@@@@@@@#++#=#==-:-==++-#@@@%==--=%@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@#***=-#@*+=#@=+==++=+==*##--%+==+%@@@@@@@*=+**+==-::::-===%@@@##@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@%#+====-+*+**+#*=+#%###*=-*+--=+****+===*#===-=*#*+=+=*@@@@@#+*@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@%+-+@@@#+==+*+=**@@@%+=+*#%##**-=#+=--+-=-=+#+==-:-#*+++*+*@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@-=-----====+*++*%@@@@%=++++%%*****==#%####+=:-::=%=:-++*+%@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@*+++***#+++=+**+#@@@@@@+=+++#@@%#*+==------=+*@%=-=+**+#@@@%**+----++*%@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@%#*#*+=++#@@@@@%*=+=+*@@%%%@@@@%%%@@#--+**+#%@@@%+===+****+---=#@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@%*##*+*#*#%%%@@@%+=-==%#****+=+%-:=++**%@@@@@%=====*==+##*+=-==#@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@%*@@@@@%*######%%%%%%%%##*++==----::-=+***%@@@@@@@%+-+%@@@@@@#+*#*+==-#@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@%++#@@@@@@@@#*##%%%%%%%%%%%#***##*****###%%%%#%@@@@@%%+-#@@@@@@@@%+##*+==-%@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@%==%@@@@*#@@@@@@%#*##%%%%%@%#****##****##%%%#+++*++#%%%%%%%%%@@@@@@@+*##*+++-@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@+#@@@@%+*@@%*@@@@@@@@@%###%#******#****+**###*++=+==--#%%%%%##%@@@@@++##*+++-%@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@%+*@@@%#@@@@@@@@@@@@@-#*++***++=---+*#%%%%##**+---+%%%%%##%@@@@+*#**+++-#@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@%+*@@@@@@@@@@@@@@@@@@%+#++++++=----=+*#%*%%%%#+=-==*%%%%%%%@@@@*+#**+=++=#@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@%%@@@@@@@@@@@@@@@@@@@%**+++===-:::-=++*#*%%#*+--=++%%%%%%%%%%%*=##*+=+**-%@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%=*+++==---::-=+**#%**+===+*#%%%%%%%%%%#=+***++***++@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%%-*+++===--::-+++#%%*+===+#%%%%%%%%%%*=+**++=+****=@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%%%%%##*+++++==---=+++*%%##+=++=-+%%%#+==+*****+=+****+#@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@%*#@@%%%#=#%@%*++++++====+***%%%%+=+**+***++++*+++++++++*##**@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@#+*%%%%%#+#%%%%@%*+++++++==+**#%@@@%###*##****+==++**++***##*#@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@%+*%%%%%%#+#####%%@%*****++++*###%%%%##*++=##**+++++++***####*%@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@%%%%%+###*##%%@@@#********#%@@%%***+=-=:###**##########*#@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@%%%%%%##%%##%@@%%#%####***##%%@%%%#**+====%%%%%%%%%###*#@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@%%%%%%%%%%%%%%#%%%%%%%##############%%@@%%##**+++*%%%%%%####%%@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@%%%%%%%%%%%%%######%%%%%%%#**********#%%%%%%%%%%%%%**#######%%%%%%%@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@%%%%%%%%%%%%#***+*****#%@@@%%%***************###%%%%%##**%%%%%%%%%%%@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@%%%%%%%%#=*+****###*##%%###%%%%%*####%%%%%%%%%@@%%%#**%@@@@@%@@@@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@%%%%%%%%%%%%**#%=*+=+*#%@@@+*++#@@@@@@@@@@@@@@@%%%#*+*+==++===+===%%%%%%%%%@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@%%%%@@@=++**%@@@@@*+++*@@@@@@@@@@*++++*%@*==*%#*====-=**+%%%%%%%%@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@%%%%%%%%%@@@@@@@@%%%%@@@@@@@@@#+***%@@@+=-=*@@%#++==+#%%%%%%%@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@%%%%%%%%%%%%%%%%%%%%%%@@@@@@@%%%@@@@%+++++%%%@%#**###%%%%%%@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%%%%%%%%%%%%@@@@@@@@@@@@%%@@@@@%@@%%%%%%%%%%%%%%@@@@@@@@@@@@@@@@@@@@@@\n" +
      "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%%%%%%%%%%%%%%@@@@@@@@@@@@@@@@@@@@@@@@@";
}
