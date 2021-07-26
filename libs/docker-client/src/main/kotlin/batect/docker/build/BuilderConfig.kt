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

package batect.docker.build

import batect.docker.api.BuilderVersion
import batect.docker.build.buildkit.BuildKitSession
import batect.docker.build.legacy.ImageBuildContext
import batect.docker.pull.RegistryCredentials

sealed class BuilderConfig(val builderVersion: BuilderVersion)

data class LegacyBuilderConfig(val registryCredentials: Set<RegistryCredentials>, val context: ImageBuildContext) : BuilderConfig(BuilderVersion.Legacy)
data class BuildKitConfig(val session: BuildKitSession) : BuilderConfig(BuilderVersion.BuildKit)
