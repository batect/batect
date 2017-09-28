/*
   Copyright 2017 Charles Korn.

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

package batect.model.events

import batect.config.Container
import batect.docker.DockerImageBuildProgress
import batect.logging.Logger

data class ImageBuildProgressEvent(val container: Container, val progress: DockerImageBuildProgress) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {}

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', current step: ${progress.currentStep}, total steps: ${progress.totalSteps}, message: '${progress.message}')"
}
