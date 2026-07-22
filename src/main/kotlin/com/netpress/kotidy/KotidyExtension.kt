package com.netpress.kotidy

/**
 * Registered as the `kotidy {}` project/task extension. `style` picks the
 * default rendering (see Style's flag values: classic/fd/fs/fv) -- overridable
 * per-invocation without touching build.gradle.kts via `-Dkotidy.style=fd`,
 * the same runtime-override convention gradle-test-logger-plugin's own
 * `-Dtestlogger.theme=` uses.
 */
open class KotidyExtension {
    var style: String = Style.CLASSIC.flag
}
