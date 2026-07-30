# Writing tests with Kotest's `DescribeSpec`

How we structure Kotlin tests across these projects (`kotidy` itself,
[`humane-kotlin`](https://github.com/woodie/humane-kotlin),
[`huck`](https://github.com/woodie/huck),
[`next-caltrain-kotlin`](https://github.com/woodie/next-caltrain-kotlin)) --
context/lifecycle conventions and mocking/stubbing patterns, using
[Kotest](https://kotest.io)'s `DescribeSpec` for `describe`/`context`/`it`
structure and `shouldBe`/matcher assertions. The Go side of this pairing
([`gorderly`](https://github.com/woodie/gorderly),
[`expect`](https://github.com/woodie/expect)) and the Swift side
([`xctidy`](https://github.com/woodie/xctidy)) follow the same shape with
different tools -- see those repos' own `docs/FRAMEWORK.md` if you're
working on that side instead. For `kotidy`'s own installation/usage, see
[the README](../README.md); this doc is about what goes *inside* a spec.

## Why not `expect()`?

Kotlin already has a native idiom for this -- infix `shouldBe`/matchers --
and the only real `expect()`-syntax option, [Atrium](https://atrium-kt.org),
is still alpha with a thin ecosystem. The specific bugs RSpec's `expect`
fixed in Ruby (a monkey-patched `.should` breaking on proxies/delegators,
global namespace pollution) don't apply here either, since Kotlin's
`shouldBe` is a statically-resolved extension function, not a runtime patch
onto `Any`. `shouldBe` stays the default.

## `describe`/`context`, `beforeEach`/`afterEach`

`DescribeSpec` gives `describe` and `context` -- `context` is Kotest's own
alias for `describe`, so either reads naturally depending on what a given
level is naming (a method under test vs. a condition on it). `beforeEach`
reruns fresh before every `it` beneath it, from the outermost `describe`
inward, same as any other xUnit-style `before` hook -- there's no separate
"shared context" mechanism to learn beyond where you place the closure:

```kotlin
describe("Humane.humanSize") {
    var bytes = 0L
    val subject = { Humane.humanSize(bytes) }

    context("with 0 bytes") {
        beforeEach { bytes = 0 }

        it("formats as Zero KB, matching ByteCountFormatter's own wording") {
            subject() shouldBe "Zero KB"
        }
    }

    context("with a gigabyte-scale value") {
        beforeEach { bytes = 5_240_000_000 }

        it("keeps 2 decimal places at 3 significant figures (not truncated to 1)") {
            subject() shouldBe "5.24 GB"
        }
    }
}
```

(`humane-kotlin`'s own `HumanSizeSpec.kt`.)

### The "subject" pattern

Kotlin has no built-in `subject`/`let` keyword, but the idea translates
directly: declare whatever it depends on as a `var` in the enclosing
`describe`, define `subject` as a closure over it, and let each `context`'s
own `beforeEach` set that `var` to whatever it needs. `subject` doesn't run
until called, so `subject()` inside every `it` always reflects whichever
`beforeEach` most recently ran:

```kotlin
describe("colorizePass") {
    lateinit var style: Style
    val subject = { colorizePass(style, "does a thing", 0.5, plain) }

    context("classic") {
        beforeEach { style = Style.CLASSIC }
        it("shows a checkmark glyph and the elapsed seconds") {
            subject() shouldBe "✔ does a thing (0.5000 seconds)"
        }
    }

    context("fd") {
        beforeEach { style = Style.FD }
        it("shows only the plain name, no glyph") {
            subject() shouldBe "does a thing"
        }
    }
}
```

(`kotidy`'s own `StylesSpec.kt`.) `humane-kotlin`'s `DistanceInTimeSpec.kt`
takes this further -- a `subject` closing over one input (`at`) shared by
a dozen sibling `context`s, nested under outer `describe` blocks that each
fix a different set of *options* (`includeSeconds`, `approximate`) baked
into the same `subject` closure. Read that file if you want the pattern
used with more than one independently-overridable input.

### `justBeforeEach`: separate "what varies" from "the action under test"

`beforeEach` alone means every `context` that needs the same act run
against different inputs either duplicates that act in each context's own
`beforeEach`, or hides it behind a `subject` closure called explicitly in
every `it`. [`kwick`](https://github.com/woodie/kwick)'s `justBeforeEach`
closes that gap: it runs after every `beforeEach` at every nesting level
has finished, immediately before the `it`, so the action under test can
live once at the parent level while each `context` only states what's
different about its own inputs:

```kotlin
describe("#divide()") {
    var numerator = 0
    var denominator = 0
    var result = 0
    justBeforeEach { result = calculator.divide(numerator, denominator) }

    context("dividing evenly") {
        beforeEach { numerator = 10; denominator = 2 }
        it("returns the quotient") { result shouldBe 5 }
    }

    context("dividing with a remainder") {
        beforeEach { numerator = 7; denominator = 2 }
        it("truncates toward zero") { result shouldBe 3 }
    }
}
```

Real usage assigns straight into a shared `var` inside `justBeforeEach`,
the same way `beforeEach` does everywhere else in this doc -- not through
a `subject := { ... }`-style closure called inside each `it`. That makes
`justBeforeEach` the default over the closure-`subject` pattern above
whenever a nested `context` needs to change an input the shared act
depends on; reach for computed-once locals instead (below) when the value
never varies per test. `next-caltrain-kotlin`'s `CaltrainServiceSpec.kt`
(`#routes()`) and `GoodTimesSpec.kt` (`debugOverrideDotw`) are the real
versions of this shape; `kwick`'s own `JustBeforeEachSpec.kt` is the
dogfood suite for the hook itself.

### `afterEach` for cleanup

Anything a `beforeEach`/`justBeforeEach` sets as global or static state
should get reset in a matching `afterEach`, so one context's override
can't leak into a sibling context or a later spec:

```kotlin
context("when 'today' is fixed via debugOverrideDotw") {
    var dotw = 0
    justBeforeEach {
        GoodTimes.debugOverrideDotw = dotw
        gt = GoodTimes()
    }
    afterEach { GoodTimes.debugOverrideDotw = null }

    // ...
}
```

(`next-caltrain-kotlin`'s own `GoodTimesSpec.kt`.)

### Skipping and focusing tests

Kotest's `DescribeSpec` supports the same `x`/`f` prefix convention Quick
uses -- `xdescribe`/`xcontext`/`xit` disable a group or single test (still
listed in output, never run); `fdescribe`/`fcontext`/`fit` focus down to just
that group or test, skipping everything else in the spec:

```kotlin
xdescribe("still needs a real fixture") {
    // ...none of the code in this closure will run.
}

it("returns the cached value") { /* ... */ }
xit("handles the timeout case") {
    // ...this one test is skipped; siblings still run.
}

fdescribe("the bug we're chasing right now") {
    // ...only this group (and other focused tests) run; everything else is skipped.
}
```

Same caveat as Quick's own `fit`/`fdescribe`: focus only works on tests Kotest
has already discovered by the time it evaluates the flag, so it doesn't
reliably reach into a test nested under an unfocused parent -- put `f` on the
level you actually want to isolate, not a leaf buried under a plain `describe`.
See `gorderly`'s and `xctidy`'s own `docs/FRAMEWORK.md` for the Go/Swift
equivalents.

### Computed-once context locals vs. `subject`

Kotest builds a spec's whole test tree in one pass -- the closure passed
to `describe`/`context` runs exactly once (per spec instance) to register
every `it` beneath it, not once per `it`. That means a plain `val`
declared directly inside a `context` body (not inside `beforeEach`) is
computed a single time and shared, read-only, across every sibling `it` in
that context:

```kotlin
describe("#routes()") {
    val schedule = SpecFixtures.weekdayOnlySchedule()
    val service = CaltrainService(schedule)

    context("for a direct electric trip (San Francisco to San Jose Diridon)") {
        val routes = service.routes(SpecFixtures.sanFrancisco, SpecFixtures.sanJoseDiridon, ScheduleType.WEEKDAY)

        it("returns one direct trip") { routes shouldHaveSize 1 }
        it("uses the electric southbound train") { routes.first().id shouldBe SpecFixtures.electricSouthTrainId }
    }
}
```

(`next-caltrain-kotlin`'s `CaltrainServiceSpec.kt`.) This is safe because
`routes` is deterministic and never mutated by any `it` -- there's nothing
for a later test to accidentally see stale. Reach for `subject`/
`beforeEach` instead as soon as a nested `context` needs to *change* an
input (see `HumanSizeSpec`/`StylesSpec` above) -- that's what reruns fresh
per test rather than once per spec.

## Mocking and stubbing

### Dependency injection via a constructor-supplied factory

Rather than reaching into global state, a constructor parameter can
default to the real implementation and take a substitute for tests -- the
seam is the default argument, not a mutable var anything else could reach
into mid-test:

```kotlin
class AppModel(
    private val preferences: Preferences = Preferences.userNodeForPackage(AppModel::class.java),
    private val clientFactory: (URI) -> ScanFetching = { ScanClient(it) },
) { /* ... */ }
```

A spec just passes a different factory:

```kotlin
val model = AppModel(
    preferences = scopedPreferences(),
    clientFactory = { FakeScanFetching { fixtureScans } },
)
```

(`huck`'s own `AppModel.kt`/`AppModelSpec.kt`.) `connect()` never knows or
cares whether `clientFactory` built a real `ScanClient` or a fake --
production code is unchanged, only what a given `AppModel` instance was
constructed with.

### Scoping real state instead of faking it

Not everything worth isolating needs a fake -- sometimes the real thing is
fine as long as each test gets its own instance instead of sharing one with
production or with every other test:

```kotlin
fun scopedPreferences(): Preferences =
    Preferences.userRoot().node("com/netpress/huck/test/${UUID.randomUUID()}")
```

(`huck`'s own `AppModelSpec.kt` -- a fresh, randomly-named
`java.util.prefs.Preferences` node per call, instead of the shared user
default node, so tests never read or write real state on the machine
running them. The same instinct as Go's `t.TempDir()`: real API, disposable
instance.)

### Test doubles for a real interface

Kotlin has no Go-style struct embedding to promote unimplemented methods
for free, so a fake implementing a real interface implements every method
directly -- typically as a nullable, per-test-settable handler per method,
defaulting to `null` so an un-configured call fails loudly instead of
returning a misleadingly-empty default:

```kotlin
class FakeScanHttpClient(
    var getHandler: ((URI) -> HttpResult)? = null,
    var downloadHandler: ((URI) -> DownloadResult)? = null,
    var deleteHandler: ((URI) -> HttpResult)? = null,
) : ScanHttpClient {
    override fun get(url: URI): HttpResult =
        getHandler?.invoke(url) ?: error("FakeScanHttpClient.get: no getHandler set")
    override fun download(url: URI): DownloadResult =
        downloadHandler?.invoke(url) ?: error("FakeScanHttpClient.download: no downloadHandler set")
    override fun delete(url: URI): HttpResult =
        deleteHandler?.invoke(url) ?: error("FakeScanHttpClient.delete: no deleteHandler set")
}
```

Each test then only wires up the handler its own scenario needs:

```kotlin
val fakeHttp = FakeScanHttpClient(getHandler = { url ->
    requestedUrl = url
    HttpResult(200, body)
})
```

(`huck`'s own `ScanClientSpec.kt`/`ScanHttpClient.kt`.) A `suspend`
interface with a method genuinely not ported/exercised yet can commit to
that in the fake itself rather than a per-test lambda -- `huck`'s
`FakeScanFetching` (in `AppModelSpec.kt`) hardcodes
`throw NotImplementedError(...)` for `cachedFile`/`save`, since no spec in
that file exercises either yet, while `fetchScans`/`delete` still take a
configurable lambda.

### `mockk` for platform types you don't own

When the type to fake is a concrete platform class rather than an
interface this codebase defines (Android's `Context`, for example),
[`mockk`](https://mockk.io) stands in where there's no seam to write a
hand-rolled fake against:

```kotlin
fun makeViewModel(schedule: Schedule, origin: String, destination: String): TripViewModel =
    TripViewModel(schedule, mockk<Context>(relaxed = true)).apply {
        setOrigin(origin)
        setDestination(destination)
        refresh()
    }
```

(`next-caltrain-kotlin`'s `TripViewModelSpec.kt`.) `relaxed = true` auto-stubs
every method with a sane default -- the test only cares that `TripViewModel`
reads a couple of preference values in `init {}`, not full `Context`
behavior, so nothing else needs a real answer.

### Coroutines: `runTest` and virtual time

For `suspend fun`s, [`kotlinx-coroutines-test`](https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test)'s
`runTest` wraps the call under test with virtual time, so a real minimum-
duration delay in production code doesn't actually slow the suite down:

```kotlin
it("stores the scans, marks hasEverConnected, and persists the host") {
    runTest {
        val model = AppModel(preferences = preferences, clientFactory = { FakeScanFetching { fixtureScans } })
        model.hostInput = "scans.example.com"

        model.connect()

        model.state shouldBe ConnectionState.Connected
    }
}
```

(`huck`'s own `AppModelSpec.kt` -- `connect()`'s real 2-second minimum-
connecting-duration floor doesn't add 2 real seconds per test.) Mixing
`beforeEach` with suspend setup isn't wired up in this account yet --
`ScanClientSpec.kt`'s own comment notes it needs a coroutine-test listener
extension none of these projects currently configure -- so suspend-heavy
specs do their own setup inline inside each `it`'s own `runTest` block
rather than relying on `beforeEach`, even where a plain (non-suspend) spec
in the same project uses `beforeEach` freely.

A spec exercising a `ViewModel` that swaps Kotlin's `Dispatchers.Main` needs
that swap scoped to the whole spec, not per test, via `beforeSpec`/
`afterSpec`:

```kotlin
val testDispatcher = StandardTestDispatcher()
beforeSpec { Dispatchers.setMain(testDispatcher) }
afterSpec { Dispatchers.resetMain() }
```

(`next-caltrain-kotlin`'s `TripViewModelSpec.kt`.)

### Regression tests double as documentation

When a spec exists specifically to pin down a bug that already happened,
say so in a comment right at the setup, not just in the commit message:

```kotlin
context("when a same-named file is cached but its size doesn't match scan.size") {
    // Regression test for the stale-cache bug ScanClient.cachedFile fixed.
    it("re-downloads from scan.path instead of trusting the stale cache, overwriting it") {
        // ...
    }
}
```

(`huck`'s `ScanClientSpec.kt`; `next-caltrain-kotlin`'s
`TripViewModelSpec.kt` has the same shape for its "manual selection via
setOffset" regression coverage.) Anyone reading the spec later knows
immediately this isn't a hypothetical edge case -- removing it silently
would reintroduce a real, previously-shipped bug.

## `kotidy`'s own tests

`StylesSpec.kt` covers the pure formatting functions only (`colorizePass`/
`colorizeFail`/`formatSeconds`/`standardFooter`/...) -- no Gradle API
involved, so it's ordinary `DescribeSpec` with no mocking needed.
`KotidyPlugin.kt` itself (the `TestListener` wiring) isn't unit-tested the
same way -- verifying it means actually applying the plugin in a real
consuming project and reading real console output, or running `make
dogfood` against `kotidy`'s own suite (see `docs/COWORK.md`'s "Dogfooding
kotidy against its own suite" for why that's a `make dogfood`-only path
rather than part of `make test`). This doc is about spec-writing
conventions; see `docs/COWORK.md` for that verification story.
