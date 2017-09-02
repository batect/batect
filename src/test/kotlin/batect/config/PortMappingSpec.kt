package batect.config

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import batect.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object PortMappingSpec : Spek({
    describe("a port mapping") {
        describe("parsing from string") {
            on("parsing a valid port mapping definition") {
                val portMapping = PortMapping.parse("123:456")

                it("returns the correct local path") {
                    assertThat(portMapping.localPort, equalTo(123))
                }

                it("returns the correct container path") {
                    assertThat(portMapping.containerPort, equalTo(456))
                }
            }

            on("parsing an empty port mapping definition") {
                it("fails with an appropriate error message") {
                    assertThat({ PortMapping.parse("") }, throws(withMessage("Port mapping definition cannot be empty.")))
                }
            }

            listOf(
                    "thing:",
                    "12:",
                    ":thing",
                    ":12",
                    "thing",
                    "12",
                    "thing:12",
                    "12:thing",
                    "-1:12",
                    "12:-1",
                    "0:12",
                    "12:0",
                    " ",
                    ":"
            ).map {
                on("parsing the invalid port mapping definition '$it'") {
                    it("fails with an appropriate error message") {
                        assertThat({ PortMapping.parse(it) }, throws(withMessage("Port mapping definition '$it' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.")))
                    }
                }
            }
        }
    }
})
