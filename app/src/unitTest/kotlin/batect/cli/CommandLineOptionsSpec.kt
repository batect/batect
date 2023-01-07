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

package batect.cli

import batect.logging.FileLogSink
import batect.logging.LogSink
import batect.logging.NullLogSink
import batect.testutils.given
import batect.testutils.on
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import jnr.posix.POSIX
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object CommandLineOptionsSpec : Spek({
    describe("a set of command line options") {
        given("no log file name has been provided") {
            val options = CommandLineOptions(
                taskName = "some-task",
                configVariablesSourceFile = Paths.get("somefile.yml"),
                configVariableOverrides = mapOf("a" to "b"),
            )

            on("extending an existing Kodein configuration") {
                val originalKodein = DI.direct {
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
            val fileSystem = Jimfs.newFileSystem(Configuration.unix())
            val resolvedPath = fileSystem.getPath("some-log.log")
            val options = CommandLineOptions(taskName = "some-task", logFileName = resolvedPath)

            on("extending an existing Kodein configuration") {
                val originalKodein = DI.direct {
                    bind<String>("some string") with instance("The string value")
                    bind<POSIX>() with instance(mock())
                }

                val extendedKodein = options.extend(originalKodein)

                it("includes the configuration from the original instance") {
                    assertThat(extendedKodein.instance("some string"), equalTo("The string value"))
                }

                it("adds itself to the Kodein configuration") {
                    assertThat(extendedKodein.instance(), equalTo(options))
                }

                it("creates a file log sink to use") {
                    assertThat(extendedKodein.instance<LogSink>(), isA<FileLogSink>())
                }

                it("creates the file log sink with the expected file name") {
                    assertThat((extendedKodein.instance<LogSink>() as FileLogSink).path, equalTo(resolvedPath))
                }
            }
        }
    }
})
