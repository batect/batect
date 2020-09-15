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

@file:UseSerializers(
    DurationLoggingSerializer::class
)

package batect.docker

import batect.logging.DurationLoggingSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Duration

@Serializable
data class DockerHealthCheckConfig(
    val interval: Duration? = null,
    val retries: Int? = null,
    val startPeriod: Duration? = null,
    val command: String? = null
)
