/*
   Copyright 2017-2021 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.telemetry

import batect.os.HostEnvironmentVariables
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CIEnvironmentDetectorSpec : Spek({
    describe("a CI environment detector") {
        given("there are no environment variables") {
            val hostEnvironmentVariables = HostEnvironmentVariables()
            val detector = CIEnvironmentDetector(hostEnvironmentVariables)

            it("returns that the application is not running on a CI system") {
                assertThat(detector.detect(), equalTo(CIDetectionResult(false, null)))
            }
        }

        given("an environment variable matching one of the known CI systems is set") {
            val hostEnvironmentVariables = HostEnvironmentVariables("GO_PIPELINE_LABEL" to "1")
            val detector = CIEnvironmentDetector(hostEnvironmentVariables)

            it("returns that the application is running on a CI system and includes the name of that system") {
                assertThat(detector.detect(), equalTo(CIDetectionResult(true, "GoCD")))
            }
        }

        given("the CI_NAME environment variable has the special value that indicates Codeship") {
            val hostEnvironmentVariables = HostEnvironmentVariables("CI_NAME" to "codeship")
            val detector = CIEnvironmentDetector(hostEnvironmentVariables)

            it("returns that the application is running on a CI system and includes the name of Codeship") {
                assertThat(detector.detect(), equalTo(CIDetectionResult(true, "Codeship")))
            }
        }

        given("the CI_NAME environment variable has another value") {
            val hostEnvironmentVariables = HostEnvironmentVariables("CI_NAME" to "something else")
            val detector = CIEnvironmentDetector(hostEnvironmentVariables)

            it("returns that the application is not running on a CI system") {
                assertThat(detector.detect(), equalTo(CIDetectionResult(false, null)))
            }
        }

        given("the JENKINS_URL environment variable is set") {
            given("the BUILD_ID environment variable is set") {
                val hostEnvironmentVariables = HostEnvironmentVariables("JENKINS_URL" to "1", "BUILD_ID" to "2")
                val detector = CIEnvironmentDetector(hostEnvironmentVariables)

                it("returns that the application is running on a CI system and includes the name of Jenkins") {
                    assertThat(detector.detect(), equalTo(CIDetectionResult(true, "Jenkins")))
                }
            }

            given("the BUILD_ID environment variable is not set") {
                val hostEnvironmentVariables = HostEnvironmentVariables("JENKINS_URL" to "1")
                val detector = CIEnvironmentDetector(hostEnvironmentVariables)

                it("returns that the application is not running on a CI system") {
                    assertThat(detector.detect(), equalTo(CIDetectionResult(false, null)))
                }
            }
        }

        given("both the TASK_ID and RUN_ID environment variables are set") {
            val hostEnvironmentVariables = HostEnvironmentVariables("TASK_ID" to "1", "RUN_ID" to "2")
            val detector = CIEnvironmentDetector(hostEnvironmentVariables)

            it("returns that the application is running on a CI system and includes the name of TaskCluster") {
                assertThat(detector.detect(), equalTo(CIDetectionResult(true, "TaskCluster")))
            }
        }

        setOf(
            "CI",
            "CONTINUOUS_INTEGRATION",
            "BUILD_NUMBER",
            "RUN_ID"
        ).forEach { name ->
            given("the generic environment variable '$name' is set without any other information") {
                val hostEnvironmentVariables = HostEnvironmentVariables(name to "1")
                val detector = CIEnvironmentDetector(hostEnvironmentVariables)

                it("returns that the application is running on a CI system but no name") {
                    assertThat(detector.detect(), equalTo(CIDetectionResult(true, null)))
                }
            }
        }
    }
})
