.PHONY: build test lint format check

# ktlintFormat runs first in its own Gradle invocation, so it's fully done before
# build/check runs ktlintCheck against the result -- matches humane-kotlin/huck's
# Makefiles, and the same reasoning (build kept failing until `make format` was
# run manually first, on huck, before this ordering was adopted account-wide).
build:
	./gradlew ktlintFormat
	./gradlew build -x test

# clean, not just test -- Gradle otherwise marks the test task UP-TO-DATE on
# an unchanged run and skips re-executing it. Matches humane-kotlin/huck/
# next-caltrain-kotlin's own test targets.
test:
	./gradlew ktlintFormat
	./gradlew clean test

# Check-only, no formatting -- fails loudly on style violations instead of
# silently fixing them.
lint:
	./gradlew ktlintCheck

# Auto-fixes the mechanical stuff ktlintCheck flags. build/test/check already
# run this first; call it directly only if you want formatting alone.
format:
	./gradlew ktlintFormat

# Gradle's own `check` lifecycle task already aggregates ktlintCheck + test --
# matches humane-kotlin's Makefile exactly, no separate log-capturing needed
# the way gorderly's (Go) check target does, since Gradle's own task output
# is already terse on success and verbose on failure.
check:
	./gradlew ktlintFormat
	./gradlew clean check
