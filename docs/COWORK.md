# Picking up kotidy in a new Cowork session

Cross-project conventions (git locks, sandbox toolchain gaps, pushing, comments, code
style) are in `~/workspace/woodie/docs/COWORK.md`.

## What this is

A real Gradle plugin (`com.netpress.kotidy`) that hooks Gradle's `TestListener` API
directly, rendering Kotest's real nested tree in any of the shared `classic`/`fd`/
`fs`/`fv` styles `gorderly`/`xctidy` also document. Replaces a `TestListener` block
that used to be hand-copied byte-for-byte across `next-caltrain-kotlin`,
`humane-kotlin`, and `huck` -- one source of truth instead of three synced copies.
Two real Gradle-ecosystem plugins (`gradle-test-logger-plugin`, `kotest-gradle-
plugin`) were checked first and don't cover this (see README's "Why not an existing
plugin").

## Design decisions worth knowing

- **Configuration-cache safe.** Every mutable counter/timer resets inside
  `test.doFirst {}`, which always re-runs at real task-execution time -- not captured
  once at Gradle *configuration* time, which goes stale under
  `org.gradle.configuration-cache=true` (a real bug `next-caltrain-kotlin` used to
  have before this plugin existed).
- **`make dogfood` applies the *published* plugin to kotidy's own build**, gated
  behind `-Pdogfood`. Kotidy can't apply itself mid-compile to the same build that's
  building it -- `plugins {}` resolves before `src/main/kotlin` compiles -- so
  dogfooding only became possible once a real published version existed to apply.
  Not wired into regular `build`/`test`/`check` since it renders using whatever the
  *last published* version does, which could be stale against an uncommitted local
  change.
- **`-fv`'s unit-suffix color is `#b9e4b4`, not ANSI-16 bright green.** A real
  `vitest run` color-picker readout showed the actual shade; the old
  `ANSI_BRIGHT_GREEN` (`92`, closer to `#2ee721`) was a guess. Now a 24-bit
  true-color `ANSI_VITEST_UNIT` (`38;2;185;228;180`) since no ANSI-16 entry is
  close -- see `gorderly`'s `docs/COWORK.md` for the full note, ported
  identically here.

## Naming

Follows the family pattern: `gorderly` (Go + orderly), `xctidy` (xcodebuild + tidy),
`kotidy` (Kotlin + tidy).

## Testing

Kotest `DescribeSpec`, `justBeforeEach` over inline `subject` closures (matches the
rest of the Kotlin family). `docs/FRAMEWORK.md` here is the shared testing-convention
reference the sibling Kotlin repos point back to.

## Sandbox limitation

No network route to Gradle's own distribution server from the sandbox -- builds are
written by inspection, verified via `make build`/`make test` on the user's own Mac
(JDK 17+, Kotlin 2.2.10, Gradle 9.4.1).

## Current status

`com.netpress.kotidy` `v0.1.0` is live and resolvable from `gradlePluginPortal()`.
Consumed by `next-caltrain-kotlin`, `humane-kotlin`, and `huck` via
`id("com.netpress.kotidy") version "0.1.0"` -- no sibling checkout or composite build
needed by any of them anymore. CI has a tag-triggered publish job for future releases
(`v0.1.0` itself was published manually, since GitHub reads a tag-triggered workflow
from the commit the tag points to, and that commit predates the job).
