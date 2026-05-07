/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package io.github.oculix.build;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Maven core extension that paints OculiX brand presence across the entire
 * build pipeline. Hooks the EventSpy lifecycle:
 *
 * <ul>
 *   <li>{@code SessionStarted}  — gecko ASCII header + tagline + lime "preparing build"</li>
 *   <li>{@code ProjectStarted}  — cyan "gecko inspecting &lt;module&gt;..." marker</li>
 *   <li>{@code ProjectSucceeded} — dim "&lt;module&gt; sealed" line</li>
 *   <li>{@code ProjectFailed}   — amber "&lt;module&gt; not signed off" line</li>
 *   <li>{@code SessionEnded}    — green/red footer with wall-clock duration and a rotating tagline</li>
 * </ul>
 *
 * <p>All output goes through the SLF4J {@link Logger} so Maven's JLine
 * pipeline can interpret ANSI codes properly (cyan gecko on POSIX
 * terminals + Windows Terminal, auto-stripped on legacy cmd that can't
 * render VT100 codes).
 *
 * <p>Glyphs are Unicode on modern terminals (Windows Terminal, PowerShell 7,
 * macOS, Linux, VS Code, JetBrains) and auto-fall-back to 7-bit ASCII on
 * legacy Windows cmd / PowerShell 5.1 — see {@link #legacyConsole()}.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
@Named("oculix-build-banner")
@Singleton
public class OculixBuildBanner extends AbstractEventSpy {

  private static final Logger LOG = LoggerFactory.getLogger("oculix");

  // ANSI styling — encoded via  so the source compiles cleanly and
  // stays portable. Maven's JLine wrapper handles per-terminal interpretation:
  // colored on POSIX + Windows Terminal, stripped on legacy cmd.
  private static final String ESC   = "";
  private static final String RESET = ESC + "[0m";
  private static final String CYAN  = ESC + "[36m";
  private static final String BOLD  = ESC + "[1m";
  private static final String DIM   = ESC + "[2m";
  private static final String LIME  = ESC + "[32m";
  private static final String RED   = ESC + "[31m";
  private static final String AMBER = ESC + "[33m";

  // Glyphs sprinkled across the build output. Two flavours:
  //   - Unicode (modern): renders on PowerShell 7, Windows Terminal, macOS
  //     Terminal, every Linux terminal, VS Code, JetBrains.
  //   - ASCII (legacy):   fallback for Windows cmd that hasn't been switched
  //     to UTF-8 (chcp 65001). Without this, the UTF-8 bytes Java emits get
  //     decoded by cmd as CP850/CP437 → mojibake (e.g. 🦎 prints as ƒªÄ).
  //
  // We pick the flavour at first use via {@link #legacyConsole()} so users
  // never have to run chcp themselves. The JVM is forced to UTF-8 via
  // .mvn/jvm.config so emission is correct on capable terminals.
  private static final boolean LEGACY = legacyConsole();
  private static final String GECKO_GLYPH = LEGACY ? "<g>" : "🦎"; // 🦎
  private static final String OK_GLYPH    = LEGACY ? "(v)" : "✓";       // ✓
  private static final String NOK_GLYPH   = LEGACY ? "(x)" : "✗";       // ✗
  private static final String ARROW       = LEGACY ? ">>"  : "▸";       // ▸

  /**
   * Detect whether we're running on a Windows console that can't render
   * UTF-8 multi-byte sequences. We treat it as legacy iff: OS is Windows
   * AND none of the well-known modern-terminal env markers are set.
   *
   * <p>Markers checked:
   * <ul>
   *   <li>{@code WT_SESSION} / {@code WT_PROFILE_ID} — Windows Terminal</li>
   *   <li>{@code ConEmuPID} — ConEmu / Cmder</li>
   *   <li>{@code TERM_PROGRAM} — VS Code, JetBrains, iTerm, others</li>
   *   <li>{@code TERM} — set by mintty / git-bash / cygwin</li>
   * </ul>
   *
   * <p>If none are set on Windows we assume legacy cmd / PowerShell 5.1
   * launched standalone, which decodes stdout as the active console code
   * page (typically CP850 in en-US, CP1252 elsewhere) — neither speaks
   * UTF-8 by default. ASCII glyphs are universally safe there.
   */
  private static boolean legacyConsole() {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (!os.contains("win")) return false;
    if (System.getenv("WT_SESSION") != null) return false;
    if (System.getenv("WT_PROFILE_ID") != null) return false;
    if (System.getenv("ConEmuPID") != null) return false;
    if (System.getenv("TERM_PROGRAM") != null) return false;
    if (System.getenv("TERM") != null) return false;
    return true;
  }

  /** Header banner printed at most once per JVM. */
  private static volatile boolean headerPrinted = false;
  /** Footer banner printed at most once per JVM. */
  private static volatile boolean footerPrinted = false;
  /** Wall-clock millis at SessionStarted, used for the footer duration. */
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
        case ProjectStarted:
          handleProjectStarted(ee);
          break;
        case ProjectSucceeded:
          handleProjectSucceeded(ee);
          break;
        case ProjectFailed:
          handleProjectFailed(ee);
          break;
        case SessionEnded:
          handleEnd(ee);
          break;
        default:
          // ignore Mojo* / Fork* events — too noisy
      }
    } catch (Throwable t) {
      // Banner is decoration, never let it break the build.
      System.err.println("[oculix-banner] suppressed: " + t.getMessage());
    }
  }

  // ------------------------------------------------------------------ header

  private void handleStart() {
    synchronized (OculixBuildBanner.class) {
      if (headerPrinted) return;
      headerPrinted = true;
      startedAt = System.currentTimeMillis();
    }
    StringBuilder out = new StringBuilder();
    out.append('\n');
    out.append(CYAN).append(GECKO).append(RESET).append('\n');
    out.append(BOLD).append(CYAN).append("  OculiX  ")
        .append(GECKO_GLYPH).append("  ").append(RESET)
        .append(BOLD).append("Visual Automation IDE")
        .append(RESET).append('\n');
    out.append(DIM).append("  visual automation, your way   ::   MIT licensed")
        .append(RESET).append('\n');
    out.append(DIM).append("  https://github.com/oculix-org/Oculix")
        .append(RESET).append('\n');
    out.append(LIME).append("  ").append(ARROW)
        .append("  preparing build...").append(RESET);
    LOG.info(out.toString());
  }

  // ----------------------------------------------------- per-project markers

  private void handleProjectStarted(ExecutionEvent ee) {
    String name = projectName(ee);
    if (name.isEmpty()) return;
    LOG.info(CYAN + "  " + GECKO_GLYPH + "  inspecting " + BOLD + name + RESET
        + DIM + "  ..." + RESET);
  }

  private void handleProjectSucceeded(ExecutionEvent ee) {
    String name = projectName(ee);
    if (name.isEmpty()) return;
    LOG.info(LIME + "  " + OK_GLYPH + "  " + RESET + DIM + name + " sealed" + RESET);
  }

  private void handleProjectFailed(ExecutionEvent ee) {
    String name = projectName(ee);
    if (name.isEmpty()) return;
    LOG.warn(AMBER + "  " + NOK_GLYPH + "  " + RESET + name + DIM
        + " not signed off by the gecko" + RESET);
  }

  private static String projectName(ExecutionEvent ee) {
    try {
      if (ee.getProject() != null) {
        // Prefer the human "name" tag, fall back to the artifactId.
        String n = ee.getProject().getName();
        if (n == null || n.isEmpty()) {
          n = ee.getProject().getArtifactId();
        }
        return n == null ? "" : n;
      }
    } catch (Throwable ignore) {
    }
    return "";
  }

  // ------------------------------------------------------------------ footer

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
      out.append(LIME).append(BOLD).append("  ").append(OK_GLYPH)
          .append("  Build green").append(RESET);
      if (!duration.isEmpty()) {
        out.append(DIM).append("  in ").append(duration).append(RESET);
      }
      out.append('\n');
      out.append(DIM).append("  ").append(pickLine(SUCCESS_TAGLINES))
          .append(RESET).append('\n');
    } else {
      out.append(RED).append(BOLD).append("  ").append(NOK_GLYPH)
          .append("  Build broken").append(RESET);
      if (!duration.isEmpty()) {
        out.append(DIM).append("  after ").append(duration).append(RESET);
      }
      out.append('\n');
      out.append(AMBER).append("  ").append(pickLine(FAILURE_TAGLINES))
          .append(RESET).append('\n');
    }
    out.append(DIM).append("  https://github.com/oculix-org/Oculix").append(RESET);
    LOG.info(out.toString());
  }

  private static boolean isSuccess(ExecutionEvent ee) {
    try {
      if (ee.getSession() != null && ee.getSession().getResult() != null) {
        return !ee.getSession().getResult().hasExceptions();
      }
    } catch (Throwable ignore) {
    }
    return true;
  }

  private static String formatDuration(long ms) {
    if (ms < 1000) return ms + " ms";
    long s = ms / 1000;
    if (s < 60) return s + "." + ((ms % 1000) / 100) + "s";
    long m = s / 60;
    long sr = s % 60;
    return m + "m " + sr + "s";
  }

  /**
   * Rotate through a small pool of taglines based on currentTimeMillis().
   * Deterministic-ish per run, varied across runs — gives the build a bit
   * of personality without going random-spam.
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
