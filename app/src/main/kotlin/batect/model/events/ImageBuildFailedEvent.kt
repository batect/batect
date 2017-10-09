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

data class ImageBuildFailedEvent(val container: Container, val message: String) : PreTaskRunFailureEvent(false) {
    override val messageToDisplay: String
        get() = "Could not build image for container '${container.name}': $message"

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', message: '$message')"
}
