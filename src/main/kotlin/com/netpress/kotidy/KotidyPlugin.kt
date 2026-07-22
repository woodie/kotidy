package com.netpress.kotidy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.util.Locale

private data class Failure(
    val number: Int,
    val path: List<String>,
    val output: List<String>,
)

/**
 * Hooks every `Test` task's Gradle TestListener API directly -- the same
 * mechanism next-caltrain-kotlin/humane-kotlin/huck's own copy-pasted
 * TestListener block used, extracted here so the three repos apply one real
 * plugin instead of keeping three hand-synced copies. Walks the real nested
 * TestDescriptor.parent chain (Kotest's DescribeSpec/Gradle's own JUnit
 * Platform integration already carry the describe/context/it hierarchy
 * there) and prints a dense tree with no blank-line padding, in whichever of
 * the four shared styles (classic/fd/fs/fv) is selected. See README and
 * docs/COWORK.md.
 */
class KotidyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kotidy", KotidyExtension::class.java)

        project.tasks.withType(Test::class.java).configureEach { test ->
            wire(test, extension)
        }
    }

    private fun wire(
        test: Test,
        extension: KotidyExtension,
    ) {
        // All per-run state is declared here, inside the per-task configuration
        // closure, not at class/file scope -- each Test task gets its own
        // captured instance, and doFirst resets it at actual task-execution
        // time (not configuration time) so a second run in the same build
        // doesn't see stale counts. Matches the reasoning next-caltrain-kotlin's
        // original TestListener block documented for its own `lastPath` reset.
        var lastPath: List<String> = emptyList()
        var total = 0
        var skippedCount = 0
        var failedCount = 0
        var totalElapsedSeconds = 0.0
        val failures = mutableListOf<Failure>()
        val testFiles = linkedSetOf<String>()
        val failedTestFiles = mutableSetOf<String>()

        val colorEnabled = System.getenv("NO_COLOR") == null
        val colorize = colorizer(colorEnabled)

        // Read at render time, not configuration time, so a single
        // `-Dkotidy.style=` passed on the command line always wins regardless
        // of when the system property is actually set relative to Gradle's own
        // configuration phase.
        fun style(): Style = Style.fromFlag(System.getProperty("kotidy.style") ?: extension.style)

        // Gradle's tree has two synthetic wrapper suites above the real
        // top-level describe() ("Gradle Test Run :..." and "Gradle Test
        // Executor N"); filtering those out by name prefix is the standard
        // trick for custom Gradle test listeners.
        fun ancestry(descriptor: TestDescriptor): List<String> {
            val names = mutableListOf<String>()
            var d = descriptor.parent
            while (d != null) {
                if (!d.name.startsWith("Gradle Test")) names.add(0, d.name)
                d = d.parent
            }
            return names
        }

        test.doFirst {
            lastPath = emptyList()
            total = 0
            skippedCount = 0
            failedCount = 0
            totalElapsedSeconds = 0.0
            failures.clear()
            testFiles.clear()
            failedTestFiles.clear()
        }

        test.addTestListener(
            object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}

                override fun afterSuite(
                    suite: TestDescriptor,
                    result: TestResult,
                ) {}

                override fun beforeTest(testDescriptor: TestDescriptor) {}

                override fun afterTest(
                    testDescriptor: TestDescriptor,
                    result: TestResult,
                ) {
                    val ancestors = ancestry(testDescriptor)
                    val path = ancestors + testDescriptor.name
                    if (ancestors.isNotEmpty()) testFiles.add(ancestors[0])

                    // Print only the part of the path not already printed for
                    // the previous test -- the "dedupe shared prefix" trick
                    // that produces a real nested tree from a flat stream of
                    // leaf-test callbacks, with no blank lines.
                    val shared = path.zip(lastPath).takeWhile { (a, b) -> a == b }.count()
                    for (depth in shared until ancestors.size) {
                        // depth == 0 means ancestors[0] -- the fully-qualified
                        // spec class name -- is about to be printed for a new
                        // top-level suite. A blank line goes before every one
                        // of those, unconditionally, so each suite's block
                        // visually stands apart from whatever came before it.
                        if (depth == 0) println()
                        println("  ".repeat(depth) + ancestors[depth])
                    }

                    val elapsedSeconds = (result.endTime - result.startTime) / 1000.0
                    total++
                    totalElapsedSeconds += elapsedSeconds
                    val style = style()

                    val line =
                        when (result.resultType) {
                            TestResult.ResultType.SUCCESS ->
                                colorizePass(style, testDescriptor.name, elapsedSeconds, colorize)

                            TestResult.ResultType.SKIPPED -> {
                                skippedCount++
                                colorizeSkip(style, testDescriptor.name, elapsedSeconds, colorize)
                            }

                            else -> {
                                failedCount++
                                if (ancestors.isNotEmpty()) failedTestFiles.add(ancestors[0])
                                val number = failures.size + 1
                                failures.add(
                                    Failure(number, path, result.exceptions.map { it.message ?: it.toString() }),
                                )
                                colorizeFail(style, testDescriptor.name, elapsedSeconds, number, colorize)
                            }
                        }
                    println("  ".repeat(ancestors.size) + line)

                    lastPath = path
                }
            },
        )

        test.doLast {
            if (failures.isNotEmpty()) {
                println()
                println("Failures:")
                failures.forEach { failure ->
                    println()
                    println("  ${failure.number}) ${failure.path.joinToString(" ")}")
                    failure.output.forEach { line -> println("     $line") }
                }
            }

            println()
            val style = style()
            if (style == Style.FV) {
                val passed = total - failedCount - skippedCount
                val filesPassed = testFiles.size - failedTestFiles.size
                println(vitestSummaryLine("Test Files", failedTestFiles.size, filesPassed, 0, testFiles.size, colorize))
                println(vitestSummaryLine("Tests", failedCount, passed, skippedCount, total, colorize))
                println(String.format(Locale.ROOT, "%11s", "Duration") + "  " + formatVitestDuration(totalElapsedSeconds))
            } else {
                standardFooter(failedCount, skippedCount, total, totalElapsedSeconds, colorize).forEach(::println)
            }
        }
    }
}
