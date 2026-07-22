plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
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
    plugins {
        create("kotidy") {
            id = "com.netpress.kotidy"
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
// it hooks Gradle's TestListener API directly rather than parsing text. Plain
// useJUnitPlatform() output for kotidy's own suite is the honest tradeoff.
// See docs/COWORK.md.
tasks.withType<Test> {
    useJUnitPlatform()
}
