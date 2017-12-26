/*
   Copyright 2017 Charles Korn.

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

package batect.cli

import batect.logging.FileLogSink
import batect.logging.LogMessageWriter
import batect.logging.LogSink
import batect.logging.NullLogSink
import batect.logging.StandardAdditionalDataSource
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.FileSystem

object CommandLineOptionsSpec : Spek({
    describe("a set of command line options") {
        given("no log file name has been provided") {
            val options = CommandLineOptions(taskName = "some-task")

            on("extending an existing Kodein configuration") {
                val originalKodein = Kodein {
                    bind<String>("some string") with instance("The string value")
                }

                val extendedKodein = options.extend(originalKodein)

                it("includes the configuration from the original instance") {
                    assertThat(extendedKodein.instance<String>("some string"), equalTo("The string value"))
                }

                it("adds itself to the Kodein configuration") {
                    assertThat(extendedKodein.instance<CommandLineOptions>(), equalTo(options))
                }

                it("creates a null log sink to use") {
                    assertThat(extendedKodein.instance<LogSink>(), isA<NullLogSink>())
                }

            }
        }

        given("a log file name has been provided") {
            val options = CommandLineOptions(taskName = "some-task", logFileName = "some-log.log")

            on("extending an existing Kodein configuration") {
                val fileSystem = Jimfs.newFileSystem(Configuration.unix())

                val originalKodein = Kodein {
                    bind<String>("some string") with instance("The string value")
                    bind<FileSystem>() with instance(fileSystem)
                    bind<LogMessageWriter>() with instance(mock())
                    bind<StandardAdditionalDataSource>() with instance(mock())
                }

                val extendedKodein = options.extend(originalKodein)

                it("includes the configuration from the original instance") {
                    assertThat(extendedKodein.instance<String>("some string"), equalTo("The string value"))
                }

                it("adds itself to the Kodein configuration") {
                    assertThat(extendedKodein.instance<CommandLineOptions>(), equalTo(options))
                }

                it("creates a file log sink to use") {
                    assertThat(extendedKodein.instance<LogSink>(), isA<FileLogSink>())
                }

                it("creates the file log sink with the expected file name") {
                    assertThat((extendedKodein.instance<LogSink>() as FileLogSink).path, equalTo(fileSystem.getPath("some-log.log")))
                }
            }
        }
    }
})
