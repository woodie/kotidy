package com.netpress.kotidy

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// Covers the pure formatting functions only -- no Gradle API involved, unlike
// KotidyPlugin itself, which wires these into a real TestListener (see
// docs/COWORK.md for why that half isn't unit-tested the same way). Every
// case here uses colorizer(false) (`plain`) so expected strings stay
// ANSI-free and readable; "colors" below is the one place actual escape-code
// wrapping is exercised, so it isn't duplicated per style.
class StylesSpec :
    DescribeSpec({
        val plain = colorizer(false)

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

            context("fs") {
                beforeEach { style = Style.FS }

                it("shows a checkmark glyph and the name, no timing") {
                    subject() shouldBe "✔ does a thing"
                }
            }

            context("fv") {
                beforeEach { style = Style.FV }

                it("shows a checkmark glyph and millisecond timing") {
                    subject() shouldBe "✓ does a thing 500ms"
                }
            }
        }

        describe("colorizeFail") {
            lateinit var style: Style
            val subject = { colorizeFail(style, "does a thing", 0.5, 1, plain) }

            context("classic") {
                beforeEach { style = Style.CLASSIC }

                it("shows a cross glyph, failure number, and elapsed seconds") {
                    subject() shouldBe "✖ does a thing (FAILED - 1) (0.5000 seconds)"
                }
            }

            context("fd") {
                beforeEach { style = Style.FD }

                it("shows the name and failure number, no glyph") {
                    subject() shouldBe "does a thing (FAILED - 1)"
                }
            }

            context("fs") {
                beforeEach { style = Style.FS }

                it("shows an x glyph, name, and failure number") {
                    subject() shouldBe "✗ does a thing (FAILED - 1)"
                }
            }

            context("fv") {
                beforeEach { style = Style.FV }

                it("shows an x glyph and millisecond timing, no failure number") {
                    subject() shouldBe "× does a thing 500ms"
                }
            }
        }

        describe("colorizeSkip") {
            lateinit var style: Style
            val subject = { colorizeSkip(style, "does a thing", 0.5, plain) }

            context("classic") {
                beforeEach { style = Style.CLASSIC }

                it("shows a circle-slash glyph and elapsed seconds") {
                    subject() shouldBe "⊘ does a thing (0.5000 seconds)"
                }
            }

            context("fd") {
                beforeEach { style = Style.FD }

                it("shows the name marked PENDING, no glyph") {
                    subject() shouldBe "does a thing (PENDING)"
                }
            }

            context("fs") {
                beforeEach { style = Style.FS }

                it("shows a dash and the name marked SKIPPED") {
                    subject() shouldBe "- does a thing (SKIPPED)"
                }
            }

            context("fv") {
                beforeEach { style = Style.FV }

                it("shows a down-arrow glyph, no timing") {
                    subject() shouldBe "↓ does a thing"
                }
            }
        }

        describe("colors") {
            context("when enabled") {
                it("wraps text in the given ANSI code and resets afterward") {
                    colorizer(true)("[32m", "ok") shouldBe "[32mok[0m"
                }
            }

            context("when disabled") {
                it("returns the text unwrapped") {
                    plain("[32m", "ok") shouldBe "ok"
                }
            }
        }

        describe("formatSeconds") {
            context("under one second") {
                it("keeps four decimal places") {
                    formatSeconds(0.1234567) shouldBe "0.1235"
                }
            }

            context("one second or more") {
                it("rounds to two decimal places") {
                    formatSeconds(12.3456) shouldBe "12.35"
                }
            }
        }

        describe("formatVitestDurationParts") {
            context("under 1000ms") {
                it("returns whole milliseconds") {
                    formatVitestDurationParts(0.5) shouldBe ("500" to "ms")
                }
            }

            context("1000ms or more") {
                it("returns seconds to two decimal places") {
                    formatVitestDurationParts(1.5) shouldBe ("1.50" to "s")
                }
            }
        }

        describe("vitestSummaryLine") {
            it("right-justifies the label to 11 columns and joins counts with total") {
                vitestSummaryLine("Tests", 1, 2, 3, 6, plain) shouldBe
                    "      Tests  1 failed | 2 passed | 3 skipped (6)"
            }

            it("falls back to \"0 passed\" when every count is zero") {
                vitestSummaryLine("Tests", 0, 0, 0, 0, plain) shouldBe "      Tests  0 passed (0)"
            }
        }

        describe("standardFooter") {
            context("with no failures") {
                it("reports Test Succeeded") {
                    standardFooter(0, 1, 5, 2.0, plain) shouldBe
                        listOf("Test Succeeded", "Tests Passed: 0 failed, 1 skipped, 5 total (2.00 seconds)")
                }
            }

            context("with at least one failure") {
                it("reports Test Failed") {
                    standardFooter(1, 0, 5, 2.0, plain) shouldBe
                        listOf("Test Failed", "Tests Passed: 1 failed, 0 skipped, 5 total (2.00 seconds)")
                }
            }
        }
    })
