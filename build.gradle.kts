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
        }
    }
}

// No custom TestListener reporter here, unlike the three consumer repos this
// plugin serves -- a Gradle plugin can't apply itself to format its own
// build's test task, since the plugins{} block above resolves before this
// project's own classes are compiled. gorderly/xctidy dogfood themselves by
// shelling out to go test/swift test as a subprocess and formatting the
// captured output in the same process; kotidy has no equivalent seam because
// it hooks Gradle's TestListener API directly rather than parsing text. See
// docs/COWORK.md.
//
// testLogging below is Gradle's own built-in per-test logging, not kotidy --
// flat "ClassName > testName PASSED", no nested describe/context tree.
// Without it, Gradle's default lifecycle log level prints nothing per test
// at all (only BUILD SUCCESSFUL/FAILED), which fails this account's own
// "lint and test are always verbose" convention (see Makefile).
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
