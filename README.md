# kotidy

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](build.gradle.kts)
[![CI](https://github.com/woodie/kotidy/actions/workflows/CI.yml/badge.svg)](https://github.com/woodie/kotidy/actions/workflows/CI.yml)
[![Release](https://img.shields.io/github/v/release/woodie/kotidy.svg)](https://github.com/woodie/kotidy/releases/latest)
[![License](https://img.shields.io/github/license/woodie/kotidy.svg)](LICENSE)

![Example Screenshot](docs/example.png)

RSpec style output for Kotest's `DescribeSpec`, via a real Gradle plugin --
no CLI wrapper or text-output parsing required.

`kotidy` hooks Gradle's own `TestListener` API directly and walks the real
nested `TestDescriptor.parent` chain Kotest's `DescribeSpec`/Gradle's JUnit
Platform integration already carry, re-rendering it as a dense, deduped tree
with no blank-line padding between `describe`/`context` groups. Unlike
[`gorderly`](https://github.com/woodie/gorderly) (Go) and
[`xctidy`](https://github.com/woodie/xctidy) (Swift), there's no raw test
output to parse -- Kotest's own spec tree is already structured, so `kotidy`
reads it straight from the Gradle API instead of reverse-engineering it from
text.

## Installation

Published and approved on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.netpress.kotidy):

```kotlin
plugins {
    id("com.netpress.kotidy") version "0.1.0"
}
```

with `gradlePluginPortal()` in the consumer's `pluginManagement.repositories`.
No sibling checkout needed -- `next-caltrain-kotlin`, `humane-kotlin`, and
`huck` all consume it this way. That's it -- every `Test` task in the
project gets the tree renderer automatically, no further wiring needed.

## Usage

```kotlin
kotidy {
    style = "fd" // classic (default), fd, fs, or fv
}
```

Or override at runtime without touching `build.gradle.kts`, the same
`-Dtestlogger.theme=`-style convention
[`gradle-test-logger-plugin`](https://github.com/radarsh/gradle-test-logger-plugin)
already established:

```
./gradlew test -Dkotidy.style=fd
```

## Output styles

Four named styles, matching the same shared surface
[`gorderly`](https://github.com/woodie/gorderly#output-styles)/
[`xctidy`](https://github.com/woodie/xctidy) already document -- picking
`kotidy` up after either of those means the table below is already familiar.

| `style` | Convention | Look |
|---|---|---|
| `classic` (default) | Our base formatter | Glyph + `name (N seconds)`, failures add `(FAILED - N)` |
| `fd` | RSpec's doc format | Plain colored name, yellow `(PENDING)` for skips |
| `fs` | Mocha's spec format | Green `✔` + gray name, red `✗ name (FAILED - N)` |
| `fv` | Vitest's own tree | Green `✓ name`, two-toned green `2ms`, red `× name`, dim gray `↓ name` |

The first three end with the same xcbeautify-style verdict + counts footer
`gorderly`/`xctidy` use; `fv` ends with Vitest's own
`Test Files`/`Tests`/`Duration` footer instead, right-justified the same way.
`Test Files` counts distinct top-level spec classes, `kotidy`'s equivalent of
one Go package or one Swift test bundle.

## Why not an existing plugin

Two real candidates exist on the Gradle side, and neither covers this gap:

[`gradle-test-logger-plugin`](https://github.com/radarsh/gradle-test-logger-plugin)
is the plugin `next-caltrain-kotlin`/`humane-kotlin`/`huck` moved off of to
begin with. Its `mocha` theme gives real nested indentation with checkmarks,
but inserts a blank line between every `describe`/`context` group with no
config flag to disable it -- confirmed still true as of its current README.
It also has no RSpec-doc-style (no-glyph) or Vitest-tree equivalent.

[`kotest-gradle-plugin`](https://github.com/kotest/kotest-gradle-plugin)
(`io.kotest`, alpha) solves a different problem: Gradle's own JUnit Platform
integration flattens nested Kotest names to leaf-only in its build output and
reports, and this plugin replaces the whole test-running task (its own
`kotest` task, not `test`) to recover the real names. It doesn't offer style
switching, and swapping the test task itself is a bigger commitment than a
`TestListener` add-on.

## Writing tests

`kotidy`'s own formatting logic (`Styles.kt`) has no Gradle API dependency --
it's tested directly with Kotest's `DescribeSpec`, the same
`describe`/`context`/`it` shape and `subject`/`beforeEach` convention every
other Kotlin repo in this account uses (see `humane-kotlin`'s own
`docs/COWORK.md` "Writing tests here"). `KotidyPlugin.kt` itself (the
`TestListener` wiring) isn't unit-tested the same way -- see `docs/COWORK.md`
for why.

## Limitations

- Tree order follows completion order, which matches declaration order for
  serial tests but can reorder under parallel test execution.
- `fv`'s per-leaf millisecond timing is only as precise as Gradle's own
  `TestResult` start/end timestamps -- sub-millisecond tests will round the
  same way `gorderly`'s own `-fv` timing does against `go test -v`'s output.
- `kotidy` can't dogfood itself against its own test suite the way
  `gorderly`/`xctidy` do (see `docs/COWORK.md`) -- its own `make test` output
  uses Gradle's plain default renderer.

## Development

```
make build   # ./gradlew ktlintFormat && ./gradlew build -x test
make test    # ./gradlew ktlintFormat && ./gradlew clean test
make lint    # ./gradlew ktlintCheck
make check   # ./gradlew ktlintFormat && ./gradlew clean check
```

## Consumed by

[`next-caltrain-kotlin`](https://github.com/woodie/next-caltrain-kotlin),
[`humane-kotlin`](https://github.com/woodie/humane-kotlin), and
[`huck`](https://github.com/woodie/huck) -- all three via the Portal
install above, replacing what used to be a byte-for-byte-copied
`TestListener` block in each repo's own `build.gradle.kts`.
