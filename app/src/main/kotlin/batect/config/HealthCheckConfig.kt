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

package batect.config

import batect.config.io.deserializers.DurationSerializer
import batect.docker.HealthCheckConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration

@Serializable
data class HealthCheckConfig(
    @Serializable(with = DurationSerializer::class) val interval: Duration? = null,
    val retries: Int? = null,
    @SerialName("start_period") @Serializable(with = DurationSerializer::class) val startPeriod: Duration? = null,
    @Serializable(with = DurationSerializer::class) val timeout: Duration? = null,
    val command: String? = null
) {
    fun toDockerHealthCheckConfig() = HealthCheckConfig(interval, retries, startPeriod, timeout, command)
}
