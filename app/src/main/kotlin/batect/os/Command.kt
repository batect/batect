/*
   Copyright 2017-2020 Charles Korn.

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

package batect.os

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.list
import kotlinx.serialization.withName

@Serializable(with = Command.Companion::class)
data class Command private constructor(val originalCommand: String, val parsedCommand: List<String>) {
    operator fun plus(newArguments: Iterable<String>): Command {
        val formattedCommand = originalCommand + formatNewArguments(newArguments)
        return Command(formattedCommand, parsedCommand + newArguments)
    }

    private fun formatNewArguments(newArguments: Iterable<String>): String {
        if (newArguments.any()) {
            return " " + newArguments
                .map { it.replace("$singleQuote", "$backslash$singleQuote") }
                .map { it.replace("$doubleQuote", "$backslash$doubleQuote") }
                .map { if (it.contains(' ')) doubleQuote + it + doubleQuote else it }
                .joinToString(" ")
        } else {
            return ""
        }
    }

    @Serializer(forClass = Command::class)
    companion object : KSerializer<Command> {
        fun parse(command: String): Command {
            val arguments = arrayListOf<String>()
            val currentArgument = StringBuilder()
            var currentMode = CommandParsingState.Normal
            var currentIndex = 0

            fun handleBackslash() {
                currentIndex++

                if (currentIndex > command.length - 1) {
                    throw danglingBackslash(command)
                }

                currentArgument.append(command[currentIndex])
            }

            while (currentIndex < command.length) {
                val char = command[currentIndex]

                when (currentMode) {
                    CommandParsingState.Normal -> when {
                        char == backslash -> handleBackslash()
                        char.isWhitespace() -> {
                            if (currentArgument.isNotBlank()) {
                                arguments += currentArgument.toString()
                            }

                            currentArgument.setLength(0)
                        }
                        char == singleQuote -> currentMode = CommandParsingState.SingleQuote
                        char == doubleQuote -> currentMode = CommandParsingState.DoubleQuote
                        else -> currentArgument.append(char)
                    }
                    CommandParsingState.SingleQuote -> when (char) {
                        singleQuote -> currentMode = CommandParsingState.Normal
                        else -> currentArgument.append(char)
                    }
                    CommandParsingState.DoubleQuote -> when (char) {
                        doubleQuote -> currentMode = CommandParsingState.Normal
                        backslash -> handleBackslash()
                        else -> currentArgument.append(char)
                    }
                }

                currentIndex++
            }

            when (currentMode) {
                CommandParsingState.DoubleQuote -> throw unbalancedDoubleQuote(command)
                CommandParsingState.SingleQuote -> throw unbalancedSingleQuote(command)
                CommandParsingState.Normal -> {
                    if (currentArgument.isNotEmpty()) {
                        arguments.add(currentArgument.toString())
                    }

                    return Command(command, arguments)
                }
            }
        }

        private const val backslash: Char = '\\'
        private const val singleQuote: Char = '\''
        private const val doubleQuote: Char = '"'

        private fun invalidCommandLine(command: String, message: String): Throwable =
            InvalidCommandLineException("Command `$command` is invalid: $message")

        private fun unbalancedDoubleQuote(command: String): Throwable =
            invalidCommandLine(command, "it contains an unbalanced double quote")

        private fun unbalancedSingleQuote(command: String): Throwable =
            invalidCommandLine(command, "it contains an unbalanced single quote")

        private fun danglingBackslash(command: String): Throwable = invalidCommandLine(
            command,
            """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')"""
        )

        private enum class CommandParsingState {
            Normal,
            SingleQuote,
            DoubleQuote
        }

        override val descriptor: SerialDescriptor = StringDescriptor.withName("command")

        override fun deserialize(decoder: Decoder): Command = try {
            parse(decoder.decodeString())
        } catch (e: InvalidCommandLineException) {
            if (decoder is YamlInput) {
                val location = decoder.getCurrentLocation()

                throw ConfigurationException(e.message ?: "", location.line, location.column, e)
            } else {
                throw e
            }
        }

        override fun serialize(encoder: Encoder, obj: Command) = StringSerializer.list.serialize(encoder, obj.parsedCommand)
    }
}

class InvalidCommandLineException(message: String) : RuntimeException(message)
