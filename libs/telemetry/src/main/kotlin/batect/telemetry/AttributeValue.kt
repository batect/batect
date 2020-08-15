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

package batect.telemetry

import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

data class AttributeValue(val json: JsonPrimitive) {
    constructor(value: String?) : this(if (value == null) JsonNull else JsonLiteral(value))
    constructor(value: Boolean?) : this(if (value == null) JsonNull else JsonLiteral(value))
    constructor(value: Int?) : this(if (value == null) JsonNull else JsonLiteral(value))
    constructor(value: Throwable) : this(describe(value))

    companion object {
        val Null = AttributeValue(JsonNull)

        private fun describe(e: Throwable): String {
            val builder = StringBuilder()

            builder.append(e::class.qualifiedName)
            builder.append('\n')

            e.stackTrace.forEach {
                builder.append("\tat ")
                builder.append(it.toString())
                builder.append('\n')
            }

            var outerException = e
            var innerException = e.cause

            while (innerException != null) {
                builder.append("Caused by: ")
                builder.append(innerException::class.qualifiedName)
                builder.append('\n')

                val innerStackTrace = innerException.stackTrace
                val outerStackTrace = outerException.stackTrace

                val pairs = outerStackTrace.reversed().zip(innerStackTrace.reversed())
                val framesInCommon = pairs.indexOfFirst { it.first != it.second }
                val interestingStackFrames = innerStackTrace.dropLast(framesInCommon)

                interestingStackFrames.forEach {
                    builder.append("\tat ")
                    builder.append(it.toString())
                    builder.append('\n')
                }

                builder.append("\t... ")
                builder.append(framesInCommon)
                builder.append(" more\n")

                outerException = innerException
                innerException = innerException.cause
            }

            return builder.toString()
        }
    }
}
