# Delivering a kotidy release

There are two ways `kotidy` reaches a consuming project, and they're not
interchangeable yet: a **Gradle composite build** (working today, no publish
step) and the **Gradle Plugin Portal** (submitted, pending approval as of
`v0.1.0`). This doc covers both, plus the checklist for cutting the next
release.

## Composite build (working now)

The consumer's `settings.gradle.kts` includes this repo from inside
`pluginManagement`:

```kotlin
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
    includeBuild("../kotidy")
}
```

and its `build.gradle.kts` applies `id("com.netpress.kotidy")` plus a
`kotidy { style = "..." }` block. This requires `kotidy` checked out as a
sibling directory on disk (`~/workspace/kotidy` next to
`~/workspace/<consumer>`) -- there's no version to resolve, so CI for a
consumer needs to check out both repos as siblings too (see
`humane-kotlin`'s own `.github/workflows/CI.yml` for the working example).
`next-caltrain-kotlin`, `humane-kotlin`, and `huck` all use this path today.

## Gradle Plugin Portal (submitted, pending approval)

`com.gradle.plugin-publish` (2.1.1) is applied, with `website`/`vcsUrl` and
the plugin's `displayName`/`description`/`tags`/`configurationCache`
compatibility declared in `build.gradle.kts`'s `gradlePlugin {}` block.

### Publishing a version

From `~/workspace/kotidy`, with a Gradle Plugin Portal API key already in
`~/.gradle/gradle.properties` (`gradle.publish.key`/`gradle.publish.secret`,
from the account's "API Keys" tab):

```
./gradlew publishPlugins --validate-only   # dry run, checks metadata only
./gradlew publishPlugins                   # real upload
```

`v0.1.0` was submitted this way on 2026-07-22. Console output on submission:

```
Your new plugin com.netpress.kotidy has been submitted for approval by
Gradle engineers. The request should be processed within the next few
days, at which point you will be contacted via email.
```

### What happens after submission

- A Gradle engineer manually reviews the plugin (first version only --
  later versions of an already-approved plugin publish immediately).
- Because the group/plugin ID is `com.netpress.*` rather than
  `io.github.<username>`, expect a possible follow-up email asking for
  proof of ownership of `netpress.com` via a DNS TXT record. The Portal
  team provides the exact record to add; this isn't a rejection, just an
  extra verification step for a corporate-style namespace.
- This happened for real on `v0.1.0`: automated checks flagged the
  submission and requested a TXT record on `netpress.com`'s root domain
  (verifiable via `dig TXT netpress.com`), with the alternative of linking
  a GitHub account and switching to `io.github.woodie.kotidy` instead --
  deliberately declined in favor of keeping `com.netpress.kotidy`, matching
  `humane-kotlin`'s existing `com.netpress` group. The TXT record has been
  added and confirmed live via `dig TXT netpress.com`. Also linked GitHub
  (`woodie`) to the Portal account, even though the plugin ID stayed
  `com.netpress.kotidy` rather than switching to `io.github.woodie.kotidy`
  -- closes the *other* common rejection reason (an unlinked GitHub account
  can't vouch for a `vcsUrl` pointing at a GitHub repo), separate from the
  domain check. Per the request email, a Gradle engineer re-reviews within
  a few days -- there's no explicit "I've fixed it, please recheck" step,
  just waiting for the next pass.
- Two possible outcomes arrive by email to the account's address (no
  status dashboard to poll): acceptance, or a list of requested changes to
  fix and resubmit.
- Once accepted, the plugin is installable from anywone's build via:

  ```kotlin
  plugins {
      id("com.netpress.kotidy") version "0.1.0"
  }
  ```

  with `gradlePluginPortal()` in the consumer's `pluginManagement.repositories`
  -- no sibling checkout needed.

### CI publish job

`.github/workflows/CI.yml` has a `publish` job gated on
`startsWith(github.ref, 'refs/tags/v')` and `needs: test`, reading
`GRADLE_PUBLISH_KEY`/`GRADLE_PUBLISH_SECRET` from repository secrets
(already added). **This only covers tags pushed after the job existed** --
GitHub reads a tag-triggered workflow from the commit the tag itself points
to, so the already-pushed `v0.1.0` tag predates this job and had to be
published manually (above). `v0.1.1` onward can rely on the tag push alone
to publish.

## Once the Portal publish is confirmed live and approved

Switch `next-caltrain-kotlin`/`humane-kotlin`/`huck` off the composite
build:

- Remove `includeBuild("../kotidy")` from each consumer's
  `pluginManagement {}` block.
- Add `gradlePluginPortal()` to each consumer's
  `pluginManagement.repositories` if not already present.
- Change `id("com.netpress.kotidy")` to
  `id("com.netpress.kotidy") version "0.1.0"` (or whatever the latest
  published version is) in each consumer's `build.gradle.kts`.
- Simplify each consumer's CI to a single checkout -- the sibling-checkout
  step for `kotidy` specifically is no longer needed.

Don't do this before the Portal listing is confirmed live -- ripping out a
working composite build in favor of a plugin ID that isn't resolvable yet
breaks all three consumers for however long approval takes.

## Pre-flight checklist for cutting a release

- [ ] `git status` clean, everything committed.
- [ ] `make check` passes on a real Mac (ktlintCheck + test together).
- [ ] Write `docs/releases/vX.Y.Z.md` (see `v0.1.0.md` for the template)
      and commit it before tagging.
- [ ] Bump `version` in `build.gradle.kts` to match the new tag.
- [ ] `git log -1` right before tagging -- confirm it's the commit you
      expect (see `~/workspace/woodie/docs/COWORK.md`'s "Tagging releases"
      for why this step exists).
- [ ] Tag (annotated, `vX.Y.Z`), then push both the commit and the tag:
      `git push origin main && git push origin vX.Y.Z`.
- [ ] Pushing the tag triggers `CI.yml`'s `publish` job automatically
      (`test` must pass first) -- no manual `./gradlew publishPlugins`
      needed for `v0.1.1` onward, unlike `v0.1.0`.
- [ ] Confirm the new version shows up on
      [plugins.gradle.org/plugin/com.netpress.kotidy](https://plugins.gradle.org/plugin/com.netpress.kotidy)
      once CI's `publish` job finishes.
