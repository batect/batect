package decompose.cli

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
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

            given("a parser with a single optional shared option") {
                val parser = OptionParser()
                val option = ValueOption("value", "The value")
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

                on("parsing a list of arguments where the option is specified in the form '--value=thing'") {
                    val result = parser.parseOptions(listOf("--value=thing", "do-stuff"))

                    it("indicates that parsing succeeded and that one argument was consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(1)))
                    }

                    it("sets the option's value") {
                        assert.that(option.value, equalTo("thing"))
                    }
                }

                on("parsing a list of arguments where the option is specified in the form '--value thing'") {
                    val result = parser.parseOptions(listOf("--value", "thing", "do-stuff"))

                    it("indicates that parsing succeeded and that two arguments were consumed") {
                        assert.that(result, equalTo<OptionsParsingResult>(ReadOptions(2)))
                    }

                    it("sets the option's value") {
                        assert.that(option.value, equalTo("thing"))
                    }
                }

                on("parsing a list of arguments where the option is given in the form '--value=thing' but no value is provided after the equals sign") {
                    val result = parser.parseOptions(listOf("--value=", "do-stuff"))

                    it("indicates that parsing failed") {
                        assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '--value=' is in an invalid format, you must provide a value after '='.")))
                    }
                }

                on("parsing a list of arguments where the option is given in the form '--value thing' but no second argument is provided") {
                    val result = parser.parseOptions(listOf("--value"))

                    it("indicates that parsing failed") {
                        assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '--value' requires a value to be provided, either in the form '--value=<value>' or '--value <value>'.")))
                    }
                }

                on("parsing a list of arguments where the option is valid but given twice") {
                    val result = parser.parseOptions(listOf("--value=thing", "--value=other-thing", "do-stuff"))

                    it("indicates that parsing failed") {
                        assert.that(result, equalTo<OptionsParsingResult>(InvalidOptions("Option '--value' cannot be specified multiple times.")))
                    }
                }
            }
        }

        // Optional value with default
        // Short version for name (eg. '--file' has '-f')
        // Check for duplicate names or short names

        // Add to commands
    }
})
