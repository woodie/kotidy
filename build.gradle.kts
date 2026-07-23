import org.gradle.plugin.compatibility.compatibility

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    // Publishes to the Gradle Plugin Portal (plugins.gradle.org) -- see
    // docs/COWORK.md for the account-history reasoning behind publishing
    // this one instead of composite-build-only like humane-kotlin. Also
    // auto-applies java-gradle-plugin and maven-publish as of 1.0.0+; kept
    // java-gradle-plugin explicit above anyway since it was already here
    // and documents intent regardless of which plugin brings it in.
    id("com.gradle.plugin-publish") version "2.1.1"
    // Portal-published v0.1.0, applied conditionally below -- not a normal
    // application, see the "Dogfooding" comment further down.
    id("com.netpress.kotidy") version "0.1.0" apply false
}

group = "com.netpress"
version = "0.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

gradlePlugin {
    website = "https://github.com/woodie/kotidy"
    vcsUrl = "https://github.com/woodie/kotidy.git"

    plugins {
        create("kotidy") {
            id = "com.netpress.kotidy"
            displayName = "kotidy"
            description = "RSpec-style nested test output for Kotest's DescribeSpec, " +
                "via a real Gradle TestListener -- classic/fd/fs/fv styles " +
                "matching gorderly (Go)/xctidy (Swift)."
            tags = listOf("testing", "kotlin", "kotest", "test-logging")
            implementationClass = "com.netpress.kotidy.KotidyPlugin"

            // Required as of plugin-publish 2.1.0+ (publishing without it is
            // deprecated, soon to be rejected outright). true because
            // KotidyPlugin.kt's counters/timing are deliberately reset in
            // doFirst {} and read real per-test TestResult timestamps rather
            // than a value captured once at configuration time -- see
            // docs/COWORK.md's "Configuration-cache safety" section for the
            // real bug (next-caltrain-kotlin's own dropped summary line) this
            // was designed around.
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

// No custom TestListener reporter here by default, unlike the three consumer
// repos this plugin serves -- a Gradle plugin can't apply *itself, mid-
// compile* to format its own build's test task, since the plugins{} block
// above resolves before this project's own classes are compiled.
// gorderly/xctidy dogfood themselves by shelling out to go test/swift test
// as a subprocess and formatting the captured output in the same process;
// kotidy has no equivalent seam because it hooks Gradle's TestListener API
// directly rather than parsing text. See docs/COWORK.md.
//
// testLogging below is Gradle's own built-in per-test logging, not kotidy --
// flat "ClassName > testName PASSED", no nested describe/context tree.
// Without it, Gradle's default lifecycle log level prints nothing per test
// at all (only BUILD SUCCESSFUL/FAILED), which fails this account's own
// "lint and test are always verbose" convention (see Makefile). Skipped
// entirely when dogfooding (-Pdogfood, see below) -- otherwise this fires
// on every test *alongside* kotidy's own applied TestListener, doubling up
// as Gradle's flat one-liner and kotidy's own nested tree line for the same
// test, interleaved. Consumer repos never hit this because they don't have
// their own testLogging {} block to begin with -- this one only exists as
// kotidy's non-dogfooded fallback.
tasks.withType<Test> {
    useJUnitPlatform()
    if (!project.hasProperty("dogfood")) {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

// Dogfooding, for docs/example.png only: the "can't apply itself" problem
// above is specifically about *this build's own not-yet-compiled classes*.
// Applying 0.1.0 *from the Gradle Plugin Portal* is a different, already-
// published artifact -- no chicken-and-egg problem at all, exactly what
// humane-kotlin/huck/next-caltrain-kotlin already do to consume kotidy
// themselves. Gated behind -Pdogfood (see `make dogfood`) rather than
// applied unconditionally: it renders using whatever the *last published*
// version's Styles.kt/KotidyPlugin.kt does, not this session's in-progress
// local changes -- letting regular `make test` quietly use stale rendering
// logic while a real change is still uncommitted would be misleading.
if (project.hasProperty("dogfood")) {
    apply(plugin = "com.netpress.kotidy")
    extensions.configure<com.netpress.kotidy.KotidyExtension> {
        style = "fs"
    }
}
