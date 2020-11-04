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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.min

@Serializable(with = Expression.Companion::class)
sealed class Expression(open val originalExpression: String) {
    abstract fun evaluate(context: ExpressionEvaluationContext): String
    abstract fun serialize(encoder: CompositeEncoder)

    companion object : KSerializer<Expression> {
        fun parse(source: String): Expression {
            val expressions = mutableListOf<Expression>()
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
                else -> ConcatenatedExpression(expressions, source)
            }
        }

        private fun readEnvironmentVariable(source: String, startIndex: Int): ParseResult =
            readVariable(source, startIndex, "environment", true) { name, default, originalExpression ->
                EnvironmentVariableReference(name, default, originalExpression)
            }

        private fun readConfigVariable(source: String, startIndex: Int): ParseResult =
            readVariable(source, startIndex, "config", false) { name, _, originalExpression ->
                ConfigVariableReference(name, originalExpression)
            }

        private fun readVariable(source: String, startIndex: Int, type: String, supportsDefaults: Boolean, creator: (String, String?, String) -> Expression): ParseResult {
            if (startIndex == source.lastIndex) {
                throw InvalidExpressionException(source, "invalid $type variable reference: '${source[startIndex]}' at column ${startIndex + 1} must be followed by a variable name")
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
                    throw InvalidExpressionException(source, "invalid $type variable reference: ':' at column ${currentIndex + 1} must be immediately followed by '-'")
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
                                throw InvalidExpressionException(source, "invalid $type variable reference: '\\' at column ${currentIndex + 1} must be immediately followed by a character to escape")
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
                throw InvalidExpressionException(source, "invalid $type variable reference: '{' at column ${startIndex + 2} must be followed by a closing '}'")
            }

            val variableName = variableNameBuilder.toString()

            if (variableName.isEmpty()) {
                throw InvalidExpressionException(source, "invalid $type variable reference: '${source.substring(startIndex, currentIndex + 1)}' at column ${startIndex + 1} does not contain a variable name")
            }

            val originalExpression = source.substring(startIndex, min(currentIndex + 1, source.lastIndex + 1))

            return ParseResult(creator(variableNameBuilder.toString(), defaultValue, originalExpression), currentIndex)
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
                            throw InvalidExpressionException(source, "invalid escape sequence: '\\' at column ${index + 1} must be immediately followed by a character to escape")
                        }

                        index++
                    }
                }

                builder.append(source[index])

                index++
            }

            val originalExpression = source.substring(startIndex, min(index + 1, source.lastIndex + 1))

            return ParseResult(LiteralValue(builder.toString(), originalExpression), index)
        }

        private data class ParseResult(val expression: Expression, val readUntil: Int)

        private val deserializationDescriptor = PrimitiveSerialDescriptor(Expression::class.qualifiedName!!, PrimitiveKind.STRING)

        private val serializationDescriptor = buildClassSerialDescriptor(Expression::class.qualifiedName!!) {
            element("type", String.serializer().descriptor)
        }

        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor(Expression::class.qualifiedName!!, SerialKind.CONTEXTUAL) {
            element("string", deserializationDescriptor)
            element("object", serializationDescriptor)
        }

        override fun deserialize(decoder: Decoder): Expression {
            try {
                val input = decoder.beginStructure(deserializationDescriptor) as YamlInput
                val result = parse(input.decodeString())
                input.endStructure(deserializationDescriptor)

                return result
            } catch (e: InvalidExpressionException) {
                if (decoder is YamlInput) {
                    throw ConfigurationException(e.message ?: "", decoder.node, e)
                } else {
                    throw e
                }
            }
        }

        override fun serialize(encoder: Encoder, value: Expression) {
            val structureEncoder = encoder.beginStructure(serializationDescriptor)

            structureEncoder.encodeStringElement(serializationDescriptor, 0, value::class.simpleName!!)
            value.serialize(structureEncoder)

            structureEncoder.endStructure(serializationDescriptor)
        }
    }
}

data class LiteralValue(val value: String, override val originalExpression: String = value) : Expression(originalExpression) {
    override fun evaluate(context: ExpressionEvaluationContext) = value
    override fun toString() = "${this::class.simpleName}(value: '$value', original expression: '$originalExpression')"

    private val descriptor: SerialDescriptor = buildClassSerialDescriptor("LiteralValue") {
        element("value", String.serializer().descriptor)
    }

    override fun serialize(encoder: CompositeEncoder) = encoder.encodeStringElement(descriptor, 0, value)
}

data class EnvironmentVariableReference(val referenceTo: String, val default: String? = null, override val originalExpression: String) : Expression(originalExpression) {
    constructor(referenceTo: String, default: String? = null) : this(referenceTo, default, if (default == null) "\${$referenceTo}" else "\${$referenceTo:-$default}")

    override fun evaluate(context: ExpressionEvaluationContext): String {
        val hostValue = context.hostEnvironmentVariables.get(referenceTo)

        return when {
            hostValue != null -> hostValue
            default != null -> default
            else -> throw ExpressionEvaluationException("The host environment variable '$referenceTo' is not set, and no default value has been provided.")
        }
    }

    private val descriptor: SerialDescriptor = buildClassSerialDescriptor("EnvironmentVariableReference") {
        element("referenceTo", String.serializer().descriptor)
        element("default", String.serializer().descriptor, isOptional = true)
    }

    override fun serialize(encoder: CompositeEncoder) {
        encoder.encodeStringElement(descriptor, 0, referenceTo)

        if (default != null) {
            encoder.encodeStringElement(descriptor, 1, default)
        }
    }

    override fun toString() = "${this::class.simpleName}(reference to: '$referenceTo', default: ${defaultValueToString()}, original expression: '$originalExpression')"

    private fun defaultValueToString() = if (default == null) {
        "null"
    } else {
        "'$default'"
    }
}

data class ConfigVariableReference(val referenceTo: String, override val originalExpression: String) : Expression(originalExpression) {
    constructor(referenceTo: String) : this(referenceTo, "<{$referenceTo}")

    override fun evaluate(context: ExpressionEvaluationContext): String {
        if (!context.configVariables.containsKey(referenceTo)) {
            throw ExpressionEvaluationException("The config variable '$referenceTo' has not been defined.")
        }

        val value = context.configVariables.getValue(referenceTo)

        if (value == null) {
            throw ExpressionEvaluationException("The config variable '$referenceTo' is not set and has no default value.")
        }

        return value
    }

    override fun toString() = "${this::class.simpleName}(reference to: '$referenceTo', original expression: '$originalExpression')"

    private val descriptor: SerialDescriptor = buildClassSerialDescriptor("ConfigVariableReference") {
        element("referenceTo", String.serializer().descriptor)
    }

    override fun serialize(encoder: CompositeEncoder) = encoder.encodeStringElement(descriptor, 0, referenceTo)
}

data class ConcatenatedExpression(val expressions: Iterable<Expression>, override val originalExpression: String) : Expression(originalExpression) {
    constructor(vararg expressions: Expression, originalExpression: String) : this(expressions.toList(), originalExpression)

    constructor(expressions: Iterable<Expression>) : this(expressions, expressions.joinToString("") { it.originalExpression })
    constructor(vararg expressions: Expression) : this(expressions.toList())

    override fun evaluate(context: ExpressionEvaluationContext): String =
        expressions.joinToString("") { it.evaluate(context) }

    private val descriptor: SerialDescriptor = buildClassSerialDescriptor("ConcatenatedExpression") {
        element("expressions", ListSerializer(Expression.serializer()).descriptor)
    }

    override fun serialize(encoder: CompositeEncoder) = encoder.encodeSerializableElement(descriptor, 0, ListSerializer(Companion), expressions.toList())

    override fun toString(): String {
        return "${this::class.simpleName}(expressions: ${expressions.joinToString()}, original expression: '$originalExpression')"
    }
}

class ExpressionEvaluationException(message: String) : RuntimeException(message)
class InvalidExpressionException(val source: String, val detail: String) : IllegalArgumentException("Invalid expression '$source': $detail")
