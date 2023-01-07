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

package batect.updates

import batect.logging.LogMessageBuilder
import batect.logging.ZonedDateTimeSerializer
import batect.primitives.Version
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime

@Serializable
data class UpdateInfo(
    val version: Version,
    val url: String,
    @Serializable(with = ZonedDateTimeSerializer::class) val lastUpdated: ZonedDateTime,
    val scripts: List<ScriptInfo>,
)

@Serializable
data class ScriptInfo(
    val name: String,
    val downloadUrl: String,
)

fun LogMessageBuilder.data(key: String, value: UpdateInfo) = data(key, value, UpdateInfo.serializer())
