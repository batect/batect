/*
   Copyright 2017-2018 Charles Korn.

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

import batect.config.BuildImage
import batect.config.PullImage
import batect.logging.Logger
import batect.model.steps.BuildImageStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.PullImageStep

object TaskStartedEvent : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        context.queueStep(CreateTaskNetworkStep)

        buildImages(context)
        pullImages(context)
    }

    private fun buildImages(context: TaskEventContext) {
        context.allTaskContainers
            .filter { it.imageSource is BuildImage }
            .forEach { context.queueStep(BuildImageStep(context.projectName, it)) }
    }

    private fun pullImages(context: TaskEventContext) {
        context.allTaskContainers
            .map { it.imageSource }
            .filterIsInstance<PullImage>()
            .toSet()
            .forEach { context.queueStep(PullImageStep(it.imageName)) }
    }

    override fun toString() = this::class.simpleName!!
}
