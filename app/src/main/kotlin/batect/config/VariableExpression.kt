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

package batect.config

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.list
import kotlinx.serialization.withName

@Serializable(with = VariableExpression.Companion::class)
sealed class VariableExpression {
    abstract fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>): String
    abstract fun serialize(encoder: Encoder)

    @Serializer(forClass = VariableExpression::class)
    companion object : KSerializer<VariableExpression> {
        fun parse(source: String): VariableExpression {
            val expressions = mutableListOf<VariableExpression>()
            var nextIndex = 0

            while (nextIndex < source.length) {
                val startIndex = nextIndex

                val result = when (source[nextIndex]) {
                    '$' -> readEnvironmentVariable(source, startIndex)
                    '<' -> readConfigVariable(source, startIndex)
                    else -> readLiteralValue(source, startIndex)
                }

                nextIndex = result.readUntil + 1
                expressions.add(result.expression)
            }

            return when (expressions.size) {
                0 -> LiteralValue("")
                1 -> expressions.single()
                else -> ConcatenatedExpression(expressions)
            }
        }

        private fun invalidExpressionException(source: String, message: String): Exception =
            IllegalArgumentException("Invalid expression '$source': $message")

        private fun readEnvironmentVariable(source: String, startIndex: Int): ParseResult =
            readVariable(source, startIndex, "environment", true) { name, default ->
                EnvironmentVariableReference(name, default)
            }

        private fun readConfigVariable(source: String, startIndex: Int): ParseResult =
            readVariable(source, startIndex, "config", false) { name, _ ->
                ConfigVariableReference(name)
            }

        private fun readVariable(source: String, startIndex: Int, type: String, supportsDefaults: Boolean, creator: (String, String?) -> VariableExpression): ParseResult {
            if (startIndex == source.lastIndex) {
                throw invalidExpressionException(source, "invalid $type variable reference: '${source[startIndex]}' at column ${startIndex + 1} must be followed by a variable name")
            }

            val hasBraces = source[startIndex + 1] == '{'
            var haveSeenClosingBrace = false
            var hasDefaultValue = false

            var currentIndex = if (hasBraces) startIndex + 2 else startIndex + 1

            val variableNameBuilder = StringBuilder()

            loop@ while (currentIndex < source.length) {
                val currentCharacter = source[currentIndex]

                if (hasBraces) {
                    when (currentCharacter) {
                        '}' -> {
                            haveSeenClosingBrace = true
                            break@loop
                        }
                        ':' -> {
                            if (supportsDefaults) {
                                hasDefaultValue = true
                                break@loop
                            }
                        }
                    }
                } else {
                    if (!(currentCharacter.isLetterOrDigit() || currentCharacter == '_')) {
                        currentIndex--
                        break@loop
                    }
                }

                variableNameBuilder.append(currentCharacter)
                currentIndex++
            }

            val defaultValue = if (!hasDefaultValue) {
                null
            } else {
                if (currentIndex == source.lastIndex || source[currentIndex + 1] != '-') {
                    throw invalidExpressionException(source, "invalid $type variable reference: ':' at column ${currentIndex + 1} must be immediately followed by '-'")
                }

                currentIndex += 2

                val defaultValueBuilder = StringBuilder()

                loop@ while (currentIndex < source.length) {
                    when (source[currentIndex]) {
                        '}' -> {
                            haveSeenClosingBrace = true
                            break@loop
                        }
                        '\\' -> {
                            if (currentIndex == source.lastIndex) {
                                throw invalidExpressionException(source, "invalid $type variable reference: '\\' at column ${currentIndex + 1} must be immediately followed by a character to escape")
                            }

                            currentIndex++
                        }
                    }

                    defaultValueBuilder.append(source[currentIndex])
                    currentIndex++
                }

                defaultValueBuilder.toString()
            }

            if (hasBraces && !haveSeenClosingBrace) {
                throw invalidExpressionException(source, "invalid $type variable reference: '{' at column ${startIndex + 2} must be followed by a closing '}'")
            }

            val variableName = variableNameBuilder.toString()

            if (variableName.isEmpty()) {
                throw invalidExpressionException(source, "invalid $type variable reference: '${source.substring(startIndex, currentIndex + 1)}' at column ${startIndex + 1} does not contain a variable name")
            }

            return ParseResult(creator(variableNameBuilder.toString(), defaultValue), currentIndex)
        }

        private fun readLiteralValue(source: String, startIndex: Int): ParseResult {
            var index = startIndex
            val builder = StringBuilder()

            loop@ while (index < source.length) {
                when (source[index]) {
                    '$', '<' -> {
                        index--
                        break@loop
                    }
                    '\\' -> {
                        if (index == source.lastIndex) {
                            throw invalidExpressionException(source, "invalid escape sequence: '\\' at column ${index + 1} must be immediately followed by a character to escape")
                        }

                        index++
                    }
                }

                builder.append(source[index])

                index++
            }

            return ParseResult(LiteralValue(builder.toString()), index)
        }

        private data class ParseResult(val expression: VariableExpression, val readUntil: Int)

        override val descriptor: SerialDescriptor = StringDescriptor.withName("expression")

        override fun deserialize(decoder: Decoder): VariableExpression = try {
            parse(decoder.decodeString())
        } catch (e: IllegalArgumentException) {
            if (decoder is YamlInput) {
                throw ConfigurationException(e.message ?: "", decoder.node.location.line, decoder.node.location.column, e)
            } else {
                throw e
            }
        }

        override fun serialize(encoder: Encoder, obj: VariableExpression) = obj.serialize(encoder)
    }
}

data class LiteralValue(val value: String) : VariableExpression() {
    override fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>) = value
    override fun serialize(encoder: Encoder) = encoder.encodeString(value)
    override fun toString() = "${this::class.simpleName}(value: '$value')"
}

data class EnvironmentVariableReference(val referenceTo: String, val default: String? = null) : VariableExpression() {
    override fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>): String {
        val hostValue = hostEnvironmentVariables.get(referenceTo)

        return when {
            hostValue != null -> hostValue
            default != null -> default
            else -> throw VariableExpressionEvaluationException("The host environment variable '$referenceTo' is not set, and no default value has been provided.")
        }
    }

    override fun serialize(encoder: Encoder) {
        val representation = if (default == null) {
            "$$referenceTo"
        } else {
            '$' + "{$referenceTo:-$default}"
        }

        encoder.encodeString(representation)
    }

    override fun toString() = "${this::class.simpleName}(reference to: '$referenceTo', default: ${defaultValueToString()})"

    private fun defaultValueToString() = if (default == null) {
        "null"
    } else {
        "'$default'"
    }
}

data class ConfigVariableReference(val referenceTo: String) : VariableExpression() {
    override fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>): String {
        if (!configVariables.containsKey(referenceTo)) {
            throw VariableExpressionEvaluationException("The config variable '$referenceTo' has not been defined.")
        }

        val value = configVariables.getValue(referenceTo)

        if (value == null) {
            throw VariableExpressionEvaluationException("The config variable '$referenceTo' is not set and has no default value.")
        }

        return value
    }

    override fun serialize(encoder: Encoder) = encoder.encodeString("<$referenceTo")
    override fun toString() = "${this::class.simpleName}(reference to: '$referenceTo')"
}

data class ConcatenatedExpression(val expressions: Iterable<VariableExpression>) : VariableExpression() {
    constructor(vararg expressions: VariableExpression) : this(expressions.toList())

    override fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>): String =
        expressions.joinToString("") { it.evaluate(hostEnvironmentVariables, configVariables) }

    override fun serialize(encoder: Encoder) {
        val serializer = VariableExpression.Companion.list

        encoder.encodeSerializableValue(serializer, expressions.toList())
    }

    override fun toString(): String {
        return "${this::class.simpleName}(expressions: ${expressions.joinToString()})"
    }
}

class VariableExpressionEvaluationException(message: String) : RuntimeException(message)
