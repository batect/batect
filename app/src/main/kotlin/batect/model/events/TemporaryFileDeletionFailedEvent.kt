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

import batect.logging.Logger
import java.nio.file.Path

data class TemporaryFileDeletionFailedEvent(val filePath: Path, val message: String) : TaskEvent() {
    override fun apply(context: TaskEventContext, logger: Logger) {
        logger.warn {
            message("Could not delete temporary file. Ignoring.")
            data("filePath", filePath)
            data("message", message)
        }
    }

    override fun toString() = "${this::class.simpleName}(file path: '$filePath', message: '$message')"
}
