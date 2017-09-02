package batect.config

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import batect.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object VolumeMountSpec : Spek({
    describe("a volume mount") {
        describe("parsing from string") {
            on("parsing a valid volume mount definition") {
                val volumeMount = VolumeMount.parse("/local:/container")

                it("returns the correct local path") {
                    assertThat(volumeMount.localPath, equalTo("/local"))
                }

                it("returns the correct container path") {
                    assertThat(volumeMount.containerPath, equalTo("/container"))
                }
            }

            on("parsing an empty volume mount definition") {
                it("fails with an appropriate error message") {
                    assertThat({ VolumeMount.parse("") }, throws(withMessage("Volume mount definition cannot be empty.")))
                }
            }

            listOf(
                    "thing:",
                    ":thing",
                    "thing",
                    " ",
                    ":"
            ).map {
                on("parsing the invalid volume mount definition '$it'") {
                    it("fails with an appropriate error message") {
                        assertThat({ VolumeMount.parse(it) }, throws(withMessage("Volume mount definition '$it' is not valid. It must be in the form 'local_path:container_path'.")))
                    }
                }
            }
        }
    }
})
