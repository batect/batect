/*
    Copyright 2017-2022 Charles Korn.

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

package batect.ui.interleaved

import batect.config.Container
import batect.config.SetupCommand
import batect.dockerclient.io.SinkTextOutput
import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import batect.ui.ConsoleColor
import batect.ui.containerio.ContainerIOStreamingOptions
import batect.ui.text.Text
import batect.ui.text.TextRun
import okio.Sink

data class InterleavedContainerIOStreamingOptions(private val output: InterleavedOutput) : ContainerIOStreamingOptions {
    override fun terminalTypeForContainer(container: Container): String? = "dumb"
    override fun useTTYForContainer(container: Container): Boolean = false
    override fun stdinForContainer(container: Container): TextInput? = null
    override fun attachStdinForContainer(container: Container): Boolean = false
    override fun stdoutForContainer(container: Container): TextOutput? = SinkTextOutput(InterleavedContainerOutputSink(container, output))
    override fun stdoutForContainerSetupCommand(container: Container, setupCommand: SetupCommand, index: Int): Sink? =
        outputStreamWithPrefix(container, "Setup command ${index + 1} | ")

    override fun stdoutForImageBuild(container: Container): Sink? = outputStreamWithPrefix(container, "Image build | ")

    private fun outputStreamWithPrefix(container: Container, prefix: String) =
        InterleavedContainerOutputSink(container, output, TextRun(Text(prefix, ConsoleColor.White)))
}
