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

package batect.logging

import batect.testutils.createForEachTest
import kotlinx.serialization.builtins.serializer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LoggerSpec : Spek({
    describe("a logger") {
        val sink by createForEachTest { mock<LogSink>() }
        val logger by createForEachTest { Logger("some.source", sink) }

        val loggerAdditionalInfo = mapOf("@source" to JsonableObject("some.source", String.serializer()))
        val buildFun: LogMessageBuilder.() -> Unit = { }

        describe("logging a debug-level message") {
            beforeEachTest { logger.debug(buildFun) }

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Debug, loggerAdditionalInfo, buildFun)
            }
        }

        describe("logging a info-level message") {
            beforeEachTest { logger.info(buildFun) }

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Info, loggerAdditionalInfo, buildFun)
            }
        }

        describe("logging a warning-level message") {
            beforeEachTest { logger.warn(buildFun) }

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Warning, loggerAdditionalInfo, buildFun)
            }
        }

        describe("logging a error-level message") {
            beforeEachTest { logger.error(buildFun) }

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Error, loggerAdditionalInfo, buildFun)
            }
        }
    }
})
