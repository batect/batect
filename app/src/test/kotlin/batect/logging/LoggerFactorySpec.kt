/*
   Copyright 2017-2019 Charles Korn.

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

import batect.Application
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.sameInstance
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object LoggerFactorySpec : Spek({
    describe("a logger factory") {
        describe("creating loggers") {
            on("creating the first logger for a class") {
                val logSink = mock<LogSink>()
                val loggerFactory = LoggerFactory(logSink)
                val logger = loggerFactory.createLoggerForClass(Application::class)

                it("returns a logger configured with the provided log sink") {
                    assertThat(logger.destination, equalTo(logSink))
                }

                it("returns a logger configured with the class' fully-qualified name") {
                    assertThat(logger.sourceName, equalTo("batect.Application"))
                }
            }

            on("creating a second logger for a class") {
                val logSink = mock<LogSink>()
                val loggerFactory = LoggerFactory(logSink)
                val firstLogger = loggerFactory.createLoggerForClass(Application::class)
                val secondLogger = loggerFactory.createLoggerForClass(Application::class)

                it("returns the same logger instance both times") {
                    assertThat(secondLogger, sameInstance(firstLogger))
                }
            }
        }
    }
})
