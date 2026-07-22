# Picking up kotidy in a new Cowork session

Context for whoever opens this repo cold, with none of the prior conversation
history. Cross-project conventions (git locks, sandbox toolchain gaps,
pushing, comments, code style) are in `~/workspace/woodie/docs/COWORK.md`.

## What this is, and why it exists

`next-caltrain-kotlin`/`humane-kotlin`/`huck` each had a byte-for-byte copy
of the same custom Gradle `TestListener` block in their own `build.gradle.kts`
-- their own comments said so explicitly ("copied byte-for-byte from
next-caltrain-kotlin's app/build.gradle.kts"). `kotidy` extracts that block
into a real Gradle plugin so there's one source of truth instead of three
hand-synced copies, following the same composite-build pattern `huck` already
uses for `humane-kotlin` (no published artifact, sibling checkout on disk --
see that repo's own `docs/COWORK.md`).

While extracting it, the original block also only ever rendered one hardcoded
style (checkmark + gray name -- closer to what `gorderly`/`xctidy` call `-fs`
than the "RSpec/ginkgo-fd-style" the old comments called it). `kotidy`
implements the full shared style surface those two repos already
document -- `classic`/`fd`/`fs`/`fv` -- rather than just relocating the
single style as-is.

## What was reviewed and rejected before building this

Two real Gradle-ecosystem plugins were checked first, since building a new
tool without checking for an existing one is exactly the mistake `gorderly`'s
own README calls out avoiding (it defers to `gotestsum` for the formats that
tool already covers well).

`gradle-test-logger-plugin` (`com.adarshr.test-logger`) is the plugin the
three consumer repos' own comments already reference and moved off of. Its
current README (checked directly, not from memory) confirms the `mocha`
theme still has no config flag to disable the blank line it inserts between
every `describe`/`context` group -- the original, still-valid reason for
moving off it. It also has no RSpec-doc (`-fd`, no-glyph) or Vitest-tree
(`-fv`) equivalent among its six themes (`plain`/`standard`/`mocha`, each
`×-parallel`).

`kotest-gradle-plugin` (`io.kotest`, alpha) solves a different, adjacent
problem: Gradle's own JUnit Platform integration flattens nested Kotest test
names to leaf-only in its build output/reports, and this plugin replaces the
whole test-running task (its own `kotest` task) to recover the real
hierarchy. No style switching, and it's a bigger commitment (swapping the
task Gradle actually runs) than a `TestListener` add-on -- also still alpha
as of the check.

Neither covers dense (no-blank-line) + `fd`/`fs`/`fv`-switchable rendering.
See README's own "Why not an existing plugin" for the same summary aimed at
a human reader.

## Configuration-cache safety

`next-caltrain-kotlin` used to print its own "N passing (Xs)" summary line
and had to drop it: it relied on a `val runStart = System.currentTimeMillis()`
captured at Gradle *configuration* time, which went stale (sometimes by
hours) whenever `org.gradle.configuration-cache=true` reused a cached
configuration instead of re-running it, producing nonsense elapsed times
like `72 passing (113516.7s)` (see that repo's own `docs/COWORK.md`).
`KotidyPlugin.kt`'s counters/timing avoid the same bug on purpose: every
mutable var (`total`, `failedCount`, `totalElapsedSeconds`, etc.) is reset
inside `test.doFirst {}`, which always re-runs at actual task-execution time
regardless of configuration-cache state -- the same fix already proven out
for `lastPath`'s own dedupe state in the original hand-copied block -- and
each test's elapsed time comes from `TestResult`'s real per-test
`startTime`/`endTime`, never a value captured once at configuration time.
`standardFooter`'s reintroduced verdict + counts line is safe under
`org.gradle.configuration-cache=true` for this reason.

## Naming

Follows the family's naming pattern: `gorderly` (Go + orderly),
`xctidy` (xcodebuild + tidy). `kotidy` (Kotlin + tidy) was picked directly
over a couple of other candidates (`gradorderly`, `kotlinly`) -- Woodie's
call, not derived from anything in the code.

## Packaging: composite build first, real publish now underway

Like `humane-kotlin`, this started with no published artifact -- but a
*plugin* composite build needs a different Gradle mechanism than a regular
library one. `huck`'s `settings.gradle.kts` pulls in `humane-kotlin` with a
plain `includeBuild("../humane-kotlin")` in the main body, because that's a
normal dependency substitution. Consuming `kotidy`'s plugin ID instead
required `pluginManagement { includeBuild("../kotidy") }` -- Gradle only
resolves a `plugins { id("com.netpress.kotidy") }` request against an
included build's declared plugin ID (see this repo's own `build.gradle.kts`
`gradlePlugin {}` block) if that build was included from inside
`pluginManagement`, not the main body. Getting this wrong produces a
"Plugin … not found" resolution error, not a silent fallback. This is
confirmed working (see "Current status" below).

Woodie then decided to actually publish `kotidy` to the Gradle Plugin Portal
instead of staying composite-build-only -- unlike every other shared library
in this account. `com.gradle.plugin-publish` (2.1.1) is now applied, with
`gradlePlugin { website; vcsUrl }` and the plugin's own `displayName`/
`description`/`tags` set. What's still outstanding, and can't happen from
this sandbox (no network route to `plugins.gradle.org`, no portal
credentials available here):

1. Woodie creates a Gradle Plugin Portal account and API key
   (`plugins.gradle.org/user/register` → API Keys tab), and adds
   `gradle.publish.key`/`gradle.publish.secret` to
   `~/.gradle/gradle.properties` (or passes them as `-P` flags).
2. `./gradlew publishPlugins --validate-only`, then `./gradlew publishPlugins`
   from a real Mac.
3. The portal's manual approval process runs (documented as "a few days").
   `com.netpress.*` as the plugin ID/group namespace may need proving
   ownership of `netpress.com` during that review, since it isn't a
   well-known pattern like `io.github.<user>`.
4. Only *after* the plugin is confirmed live and installable from the portal
   should `next-caltrain-kotlin`/`humane-kotlin`/`huck` switch from
   `pluginManagement { includeBuild("../kotidy") }` to a plain
   `id("com.netpress.kotidy") version "0.1.0"` (with `gradlePluginPortal()`
   in their `pluginManagement.repositories`) -- ripping out the working
   composite-build wiring before the portal publish is actually live would
   just break all three for however long approval takes. Their CI (once
   added) also gets simpler at that point -- no more sibling-checkout dance
   for kotidy specifically.

## Why kotidy doesn't dogfood itself

`gorderly`/`xctidy` both dogfood themselves against their own test suites
(`make test` shells out to the compiled binary itself, formatting its own
`go test`/`swift test` output in the same process). `kotidy` can't do the
equivalent: it hooks Gradle's `TestListener` API directly rather than parsing
text, which means applying it requires the plugin's own classes to already be
compiled -- but `build.gradle.kts`'s `plugins {}` block resolves before this
project's own `src/main/kotlin` is compiled, in the same build. There's no
seam to apply `com.netpress.kotidy` to the build that produces
`com.netpress.kotidy` itself. `Styles.kt` (the pure rendering logic, no
Gradle API) is unit-tested directly instead; `KotidyPlugin.kt` (the
`TestListener` wiring itself) isn't covered by an automated test here --
verifying it means actually applying it in a consuming repo and reading real
console output, which is what the three consumer repos' own `make test` runs
now do.

## Current status

Built by inspection in a sandbox with no network route to Gradle's own
distribution server (confirmed: `./gradlew clean test` fails resolving
`services.gradle.org` trying to download the pinned `gradle-9.4.1-bin.zip`
wrapper) -- matches every other Kotlin/Go/Swift repo in this account per
`~/workspace/woodie/docs/COWORK.md`'s "Working on unfamiliar stacks".

`make build` and `make test` both confirmed green on a real Mac (JDK/Gradle
9.4.1, Kotlin 2.2.10). First real `make build` caught one genuine bug
inspection missed: the ANSI escape constants in `Styles.kt` were originally
named lowerCamelCase (`ansiReset`, `ansiRed`, etc.) as `const val`s --
`ktlintMainSourceSetFormat` failed with "Property name should use the
screaming snake case notation when the value can not be changed
(cannot be auto-corrected)" for all seven. Renamed to
`ANSI_RESET`/`ANSI_RED`/etc. and it went green -- this is the opposite
naming direction from what the three consumer repos' old copy-pasted block
needed (`.editorconfig`'s `ktlint_standard_property-naming` disable there
existed because those were plain `val`s, not real `const val`s, so ktlint
wanted to *lowercase* them instead).

`make test` was also silent by design before a fix -- kotidy can't apply its
own tree renderer to its own build (see "Why kotidy doesn't dogfood itself"
above), and Gradle's default lifecycle logging prints nothing per test
without `testLogging {}` configured, so the very first real `make test` run
showed zero test output despite passing. Added `testLogging { events(...) }`
to `build.gradle.kts` -- flat `ClassName > testName PASSED` lines now, not a
tree (that only renders in a consumer that applies the plugin), but real
per-test visibility again, matching the account's verbose-test-output
convention.

`humane-kotlin`'s own `make test` confirmed the actual consumer path end to
end on a real Mac: `pluginManagement { includeBuild("../kotidy") }` resolved
correctly, `style = "fs"` rendered the real nested describe/context/it tree
with no blank-line padding within a suite, and the reintroduced `Test
Succeeded`/`Tests Passed: 0 failed, 0 skipped, 45 total (0.0570 seconds)`
footer printed correctly at the end. `next-caltrain-kotlin`/`huck` and
`make lint`/`make check` still need confirming.

## Consumed by

`next-caltrain-kotlin`, `humane-kotlin`, `huck` -- each replacing its own
copy-pasted `TestListener` block with `pluginManagement { includeBuild(...) }`
+ `plugins { id("com.netpress.kotidy") }`. See each repo's own
`docs/COWORK.md` for the specific diff and what its `.editorconfig`'s
`ktlint_standard_property-naming` override situation looks like afterward
(`humane-kotlin`/`huck` no longer need it once their own copy of the
SCREAMING_SNAKE_CASE constants is gone; `next-caltrain-kotlin` keeps its own
override regardless, for an unrelated reason -- Swift-`let`-parity const
naming in its actual app code).
