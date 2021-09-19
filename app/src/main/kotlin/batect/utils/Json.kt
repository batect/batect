/*
    Copyright 2017-2021 Charles Korn.

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

package batect.utils

import batect.config.includes.GitIncludePathResolutionContext
import batect.os.DefaultPathResolutionContext
import batect.os.PathResolutionContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

object Json {
    private val loggingModule = SerializersModule {
        include(batect.execution.model.rules.serializersModule)

        polymorphic(PathResolutionContext::class, DefaultPathResolutionContext::class, DefaultPathResolutionContext.serializer())
        polymorphic(PathResolutionContext::class, GitIncludePathResolutionContext::class, GitIncludePathResolutionContext.serializer())
    }

    val default = Json {
        encodeDefaults = true
    }

    val ignoringUnknownKeys = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val withoutDefaults = Json {
        encodeDefaults = false
    }

    val forLogging = Json {
        encodeDefaults = true
        serializersModule = loggingModule
    }
}
