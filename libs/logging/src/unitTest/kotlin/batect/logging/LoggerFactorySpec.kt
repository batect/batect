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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.sameInstance
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LoggerFactorySpec : Spek({
    describe("a logger factory") {
        describe("creating loggers") {
            describe("creating the first logger for a class") {
                val logSink = mock<LogSink>()
                val loggerFactory = LoggerFactory(logSink)
                val logger = loggerFactory.createLoggerForClass(TestLoggingClass::class)

                it("returns a logger configured with the provided log sink") {
                    assertThat(logger.destination, equalTo(logSink))
                }

                it("returns a logger configured with the class' fully-qualified name") {
                    assertThat(logger.sourceName, equalTo("batect.logging.TestLoggingClass"))
                }
            }

            describe("creating a second logger for a class") {
                val logSink = mock<LogSink>()
                val loggerFactory = LoggerFactory(logSink)
                val firstLogger = loggerFactory.createLoggerForClass(TestLoggingClass::class)
                val secondLogger = loggerFactory.createLoggerForClass(TestLoggingClass::class)

                it("returns the same logger instance both times") {
                    assertThat(secondLogger, sameInstance(firstLogger))
                }
            }
        }
    }
})

private class TestLoggingClass
