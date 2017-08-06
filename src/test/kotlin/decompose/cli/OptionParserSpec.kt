package decompose.cli

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object OptionParserSpec : Spek({
    describe("an option parser") {
        describe("parsing") {
            given("a parser with no options") {
                val parser = OptionParser()

                on("parsing an empty list of arguments") {
                    val result = parser.parseOptions(emptyList())

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(0)))
                    }
                }

                on("parsing a non-empty list of arguments") {
                    val result = parser.parseOptions(listOf("some-argument"))

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(0)))
                    }
                }
            }

            given("a parser with a single value option with a short name") {
                val parser = OptionParser()
                val option = ValueOption("value", "The value", 'v')
                parser.addOption(option)

                on("parsing an empty list of arguments") {
                    val result = parser.parseOptions(emptyList())

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(0)))
                    }

                    it("sets the option's value to null") {
                        assert.that(option.value, absent())
                    }
                }

                on("parsing a list of arguments where the option is not specified") {
                    val result = parser.parseOptions(listOf("do-stuff"))

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(0)))
                    }

                    it("sets the option's value to null") {
                        assert.that(option.value, absent())
                    }
                }

                listOf("--value", "-v").forEach { format ->
                    on("parsing a list of arguments where the option is not specified") {
                        val result = parser.parseOptions(listOf("do-stuff"))

                        it("indicates that parsing succeeded and that no arguments were consumed") {
                            assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(0)))
                        }

                        it("sets the option's value to null") {
                            assert.that(option.value, absent())
                        }
                    }

                    on("parsing a list of arguments where the option is specified in the form '$format thing'") {
                        val result = parser.parseOptions(listOf(format, "thing", "do-stuff"))

                        it("indicates that parsing succeeded and that two arguments were consumed") {
                            assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(2)))
                        }

                        it("sets the option's value") {
                            assert.that(option.value, equalTo("thing"))
                        }
                    }

                    on("parsing a list of arguments where the option is given in the form '$format=thing' but no value is provided after the equals sign") {
                        val result = parser.parseOptions(listOf("$format=", "do-stuff"))

                        it("indicates that parsing failed") {
                            assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '$format=' is in an invalid format, you must provide a value after '='.")))
                        }
                    }

                    on("parsing a list of arguments where the option is given in the form '$format thing' but no second argument is provided") {
                        val result = parser.parseOptions(listOf(format))

                        it("indicates that parsing failed") {
                            assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '$format' requires a value to be provided, either in the form '$format=<value>' or '$format <value>'.")))
                        }
                    }
                }

                setOf(
                        listOf("--value=thing", "--value=other-thing"),
                        listOf("-v=thing", "--value=other-thing"),
                        listOf("--value=thing", "-v=other-thing"),
                        listOf("-v=thing", "-v=other-thing"),
                        listOf("--value=thing", "--value", "other-thing"),
                        listOf("--value", "thing", "--value=other-thing")
                ).forEach { args ->
                    on("parsing a list of arguments where the option is valid but given twice in the form $args") {
                        val result = parser.parseOptions(args + "do-stuff")

                        it("indicates that parsing failed") {
                            assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '--value' (or '-v') cannot be specified multiple times.")))
                        }
                    }
                }
            }

            given("a parser with a single value option without a short name") {
                val parser = OptionParser()
                val option = ValueOption("value", "The value")
                parser.addOption(option)

                on("parsing a list of arguments where the option is valid but given twice") {
                    val result = parser.parseOptions(listOf("--value=thing", "--value=other-thing", "do-stuff"))

                    it("indicates that parsing failed") {
                        assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '--value' cannot be specified multiple times.")))
                    }
                }
            }

            given("a parser with a single value option with a default value") {
                val parser = OptionParser()
                val option = ValueOption("value", "The value", defaultValue = "the-default-value")
                parser.addOption(option)

                on("parsing a list of arguments where the option is not specified") {
                    val result = parser.parseOptions(listOf("do-stuff"))

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(0)))
                    }

                    it("sets the option's value to the default value given") {
                        assert.that(option.value, equalTo("the-default-value"))
                    }
                }

                on("parsing a list of arguments where the option is specified") {
                    val result = parser.parseOptions(listOf("--value=some-other-value", "do-stuff"))

                    it("indicates that parsing succeeded and that one argument was consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(1)))
                    }

                    it("sets the option's value to the value given in the argument") {
                        assert.that(option.value, equalTo("some-other-value"))
                    }
                }
            }
        }

        describe("adding options") {
            given("a parser with a single value option with a short name") {
                val parser = OptionParser()
                val option = ValueOption("value", "The value", 'v')
                parser.addOption(option)

                on("getting the list of all options") {
                    val options = parser.getOptions()

                    it("returns a list with that single option") {
                        assert.that(options, equalTo(setOf(option)))
                    }
                }

                on("attempting to add another option with the same name") {
                    it("throws an exception") {
                        assert.that({ parser.addOption(ValueOption("value", "The other value")) },
                                throws<IllegalArgumentException>(withMessage("An option with the name 'value' has already been added.")))
                    }
                }

                on("attempting to add another option with the same short name") {
                    it("throws an exception") {
                        assert.that({ parser.addOption(ValueOption("other-value", "The other value", 'v')) },
                                throws<IllegalArgumentException>(withMessage("An option with the name 'v' has already been added.")))
                    }
                }
            }
        }


        // Add to commands
    }
})
