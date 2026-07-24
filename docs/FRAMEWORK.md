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
