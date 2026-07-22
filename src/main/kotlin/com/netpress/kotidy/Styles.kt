package com.netpress.kotidy

import java.util.Locale
import kotlin.math.roundToLong

/**
 * The shared style surface across gorderly (Go)/xctidy (Swift)/kotidy (Kotlin):
 * classic is the default (no style flag), fd/fs/fv match RSpec's documentation
 * format, Mocha's spec format, and Vitest's own tree reporter respectively. See
 * README's style table.
 */
enum class Style(
    val flag: String,
) {
    CLASSIC("classic"),
    FD("fd"),
    FS("fs"),
    FV("fv"),
    ;

    companion object {
        fun fromFlag(flag: String?): Style = entries.find { it.flag == flag } ?: CLASSIC
    }
}

private const val ANSI_RESET = "[0m"
private const val ANSI_RED = "[31m"
private const val ANSI_GREEN = "[32m"
private const val ANSI_BRIGHT_GREEN = "[92m"
private const val ANSI_YELLOW = "[33m"
private const val ANSI_CYAN = "[36m"
private const val ANSI_GRAY = "[90m"

/** Respects the NO_COLOR convention (https://no-color.org/) -- caller decides enabled. */
fun colorizer(enabled: Boolean): (String, String) -> String = { code, text -> if (enabled) "$code$text$ANSI_RESET" else text }

// Matches ginkgo-fd's/gorderly's/xctidy's own precision split: sub-second runs
// (the overwhelming majority of unit tests) get enough decimals to be
// non-zero, anything slower rounds to hundredths instead of a false-precision
// tail.
fun formatSeconds(seconds: Double): String =
    if (seconds < 1) {
        String.format(Locale.ROOT, "%.4f", seconds)
    } else {
        String.format(Locale.ROOT, "%.2f", seconds)
    }

// Reproduces Vitest's own formatTime (utils.ts): whole milliseconds under
// 1000ms, seconds to two decimals at or above -- split into number/unit so
// colorizePass (-fv) can shade them two different greens the way Vitest itself
// does.
fun formatVitestDurationParts(seconds: Double): Pair<String, String> {
    val ms = seconds * 1000
    return if (ms > 1000) {
        String.format(Locale.ROOT, "%.2f", ms / 1000) to "s"
    } else {
        ms.roundToLong().toString() to "ms"
    }
}

fun formatVitestDuration(seconds: Double): String {
    val (number, unit) = formatVitestDurationParts(seconds)
    return "$number$unit"
}

// colorizePass/colorizeFail/colorizeSkip each cover one test state across all
// four styles -- matching gorderly's own colorizePass/colorizeFail/
// colorizeSkip split exactly, rather than one shared renderLeaf shape with a
// generic outer color wrap. That split matters because the styles don't just
// differ in glyph/text: classic colors only the glyph and the elapsed-time
// number, leaving the name in the terminal's default color, while fd/fs color
// the whole label as one block.

fun colorizePass(
    style: Style,
    name: String,
    elapsedSeconds: Double,
    colorize: (String, String) -> String,
): String =
    when (style) {
        Style.CLASSIC -> "${colorize(ANSI_GREEN, "✔")} $name (${colorize(ANSI_GREEN, formatSeconds(elapsedSeconds))} seconds)"
        Style.FS -> "${colorize(ANSI_GREEN, "✔")} ${colorize(ANSI_GRAY, name)}"
        Style.FV -> {
            val (num, unit) = formatVitestDurationParts(elapsedSeconds)
            "${colorize(ANSI_GREEN, "✓")} $name ${colorize(ANSI_GREEN, num)}${colorize(ANSI_BRIGHT_GREEN, unit)}"
        }
        Style.FD -> colorize(ANSI_GREEN, name)
    }

fun colorizeFail(
    style: Style,
    name: String,
    elapsedSeconds: Double,
    failureNumber: Int,
    colorize: (String, String) -> String,
): String =
    when (style) {
        Style.CLASSIC ->
            "${colorize(ANSI_RED, "✖")} $name (FAILED - $failureNumber)" +
                " (${colorize(ANSI_RED, formatSeconds(elapsedSeconds))} seconds)"
        Style.FS -> colorize(ANSI_RED, "✗ $name (FAILED - $failureNumber)")
        Style.FV ->
            // No inline "(FAILED - N)" -- Vitest's own tree doesn't number
            // failures inline either; the trailing Failures: section still
            // cross-references by number, same as gorderly/xctidy's -fv.
            colorize(ANSI_RED, "× $name ${formatVitestDuration(elapsedSeconds)}")
        Style.FD -> colorize(ANSI_RED, "$name (FAILED - $failureNumber)")
    }

fun colorizeSkip(
    style: Style,
    name: String,
    elapsedSeconds: Double,
    colorize: (String, String) -> String,
): String =
    when (style) {
        Style.CLASSIC -> "${colorize(ANSI_CYAN, "⊘")} $name (${colorize(ANSI_CYAN, formatSeconds(elapsedSeconds))} seconds)"
        Style.FS -> colorize(ANSI_CYAN, "- $name (SKIPPED)")
        Style.FV -> colorize(ANSI_GRAY, "↓ $name")
        Style.FD -> colorize(ANSI_YELLOW, "$name (PENDING)")
    }

/**
 * Reproduces Vitest's own footer shape -- a label right-justified to 11
 * columns (matching its padSummaryTitle, which does str.padStart(11)),
 * followed by "N failed | M passed | K skipped (total)", each count colored
 * the same way Vitest's getStateString does. Matches gorderly's
 * vitestSummaryLine exactly.
 */
fun vitestSummaryLine(
    label: String,
    failed: Int,
    passed: Int,
    skipped: Int,
    total: Int,
    colorize: (String, String) -> String,
): String {
    val parts = mutableListOf<String>()
    if (failed > 0) parts += colorize(ANSI_RED, "$failed failed")
    if (passed > 0) parts += colorize(ANSI_GREEN, "$passed passed")
    if (skipped > 0) parts += colorize(ANSI_GRAY, "$skipped skipped")
    if (parts.isEmpty()) parts += "0 passed"
    return String.format(Locale.ROOT, "%11s", label) + "  " + parts.joinToString(" | ") + " ($total)"
}

/** The shared classic/fd/fs footer -- matches gorderly/xctidy's xcbeautify-style verdict + counts. */
fun standardFooter(
    failedCount: Int,
    skippedCount: Int,
    total: Int,
    totalElapsedSeconds: Double,
    colorize: (String, String) -> String,
): List<String> {
    val verdict = if (failedCount > 0) "Test Failed" else "Test Succeeded"
    val verdictColor = if (failedCount > 0) ANSI_RED else ANSI_GREEN
    return listOf(
        colorize(verdictColor, verdict),
        colorize(
            verdictColor,
            "Tests Passed: $failedCount failed, $skippedCount skipped, $total total (${formatSeconds(totalElapsedSeconds)} seconds)",
        ),
    )
}
