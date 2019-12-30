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
import kotlinx.serialization.withName

@Serializable(with = VariableExpression.Companion::class)
sealed class VariableExpression {
    abstract fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>): String

    @Serializer(forClass = VariableExpression::class)
    companion object : KSerializer<VariableExpression> {
        private val patterns = listOf(
            Regex("\\$\\{(.+):-(.*)}") to { match: MatchResult -> EnvironmentVariableReference(match.groupValues[1], match.groupValues[2]) },
            Regex("\\$\\{([^:]+)}") to { match: MatchResult -> EnvironmentVariableReference(match.groupValues[1]) },
            Regex("\\$([^{](.*[^}])?)") to { match: MatchResult -> EnvironmentVariableReference(match.groupValues[1]) },
            Regex("<\\{(.+)}") to { match: MatchResult -> ConfigVariableReference(match.groupValues[1]) },
            Regex("<([^{](.*[^}])?)") to { match: MatchResult -> ConfigVariableReference(match.groupValues[1]) },
            Regex("[^$<].*") to { match: MatchResult ->
                if (match.value.startsWith("\\$") || match.value.startsWith("\\<")) {
                    LiteralValue(match.value.drop(1))
                } else {
                    LiteralValue(match.value)
                }
            }
        )

        fun parse(source: String): VariableExpression {
            patterns.forEach { (pattern, generator) ->
                val result = pattern.matchEntire(source)

                if (result != null) {
                    return generator(result)
                }
            }

            throw IllegalArgumentException("Invalid expression '$source'")
        }

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

        override fun serialize(encoder: Encoder, obj: VariableExpression) {
            val representation = when (obj) {
                is LiteralValue -> obj.value
                is EnvironmentVariableReference -> {
                    if (obj.default == null) {
                        '$' + obj.referenceTo
                    } else {
                        '$' + "{${obj.referenceTo}:-${obj.default}}"
                    }
                }
                is ConfigVariableReference -> "<${obj.referenceTo}"
            }

            encoder.encodeString(representation)
        }
    }
}

data class LiteralValue(val value: String) : VariableExpression() {
    override fun evaluate(hostEnvironmentVariables: Map<String, String>, configVariables: Map<String, String?>) = value
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

    override fun toString() = "${this::class.simpleName}(reference to: '$referenceTo')"
}

class VariableExpressionEvaluationException(message: String) : RuntimeException(message)
