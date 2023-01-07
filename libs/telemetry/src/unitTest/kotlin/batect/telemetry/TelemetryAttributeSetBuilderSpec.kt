/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.telemetry

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TelemetryAttributeSetBuilderSpec : Spek({
    describe("a telemetry attribute set builder") {
        val builder by createForEachTest { TelemetryAttributeSetBuilder() }

        describe("building a set with no attributes") {
            val attributes by createForEachTest { builder.build() }

            it("returns an empty set of attributes") {
                assertThat(attributes, equalTo(emptyMap()))
            }
        }

        describe("building a set with a string attribute") {
            val attributes by createForEachTest {
                builder.addAttribute("thingType", "stuff")
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thingType" to JsonPrimitive("stuff"))))
            }
        }

        describe("building a set with a null string attribute") {
            val attributes by createForEachTest {
                builder.addAttribute("thingType", null as String?)
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thingType" to JsonNull)))
            }
        }

        describe("building a set with an integer attribute") {
            val attributes by createForEachTest {
                builder.addAttribute("thingCount", 12)
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thingCount" to JsonPrimitive(12))))
            }
        }

        describe("building a set with a null integer attribute") {
            val attributes by createForEachTest {
                builder.addAttribute("thingCount", null as Int?)
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thingCount" to JsonNull)))
            }
        }

        describe("building a set with a boolean attribute") {
            val attributes by createForEachTest {
                builder.addAttribute("thingEnabled", false)
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thingEnabled" to JsonPrimitive(false))))
            }
        }

        describe("building a set with a null boolean attribute") {
            val attributes by createForEachTest {
                builder.addAttribute("thingEnabled", null as Boolean?)
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thingEnabled" to JsonNull)))
            }
        }

        describe("building a set with a null attribute") {
            val attributes by createForEachTest {
                builder.addNullAttribute("thing")
                builder.build()
            }

            it("stores the attribute on the built session") {
                assertThat(attributes, equalTo(mapOf("thing" to JsonNull)))
            }
        }

        describe("building a set with multiple attributes") {
            val attributes by createForEachTest {
                builder.addAttribute("thingType", "stuff")
                builder.addAttribute("thingCount", 12)
                builder.addAttribute("thingEnabled", false)
                builder.addNullAttribute("thing")
                builder.build()
            }

            it("stores all of the attributes on the built session") {
                assertThat(
                    attributes,
                    equalTo(
                        mapOf(
                            "thingType" to JsonPrimitive("stuff"),
                            "thingCount" to JsonPrimitive(12),
                            "thingEnabled" to JsonPrimitive(false),
                            "thing" to JsonNull,
                        ),
                    ),
                )
            }
        }

        describe("building a set with two attributes of the same name") {
            given("the existing attribute is a string") {
                beforeEachTest { builder.addAttribute("thing", "stuff") }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }

            given("the existing attribute is an integer") {
                beforeEachTest { builder.addAttribute("thing", 123) }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }

            given("the existing attribute is a boolean") {
                beforeEachTest { builder.addAttribute("thing", false) }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }

            given("the existing attribute is null") {
                beforeEachTest { builder.addNullAttribute("thing") }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }
        }
    }
})
