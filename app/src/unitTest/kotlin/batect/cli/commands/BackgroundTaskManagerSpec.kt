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

package batect.cli.commands

import batect.config.includes.GitRepositoryCacheCleanupTask
import batect.telemetry.TelemetryUploadTask
import batect.testutils.createForEachTest
import batect.testutils.on
import batect.wrapper.WrapperCacheCleanupTask
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BackgroundTaskManagerSpec : Spek({
    describe("a background task manager") {
        val wrapperCacheCleanupTask by createForEachTest { mock<WrapperCacheCleanupTask>() }
        val gitRepositoryCacheCleanupTask by createForEachTest { mock<GitRepositoryCacheCleanupTask>() }
        val telemetryUploadTask by createForEachTest { mock<TelemetryUploadTask>() }
        val backgroundTaskManager by createForEachTest { BackgroundTaskManager(wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, telemetryUploadTask) }

        on("starting background tasks") {
            beforeEachTest { backgroundTaskManager.startBackgroundTasks() }

            it("starts the wrapper cache cleanup task") {
                verify(wrapperCacheCleanupTask).start()
            }

            it("starts the Git repository cache cleanup task") {
                verify(gitRepositoryCacheCleanupTask).start()
            }

            it("starts the telemetry upload task") {
                verify(telemetryUploadTask).start()
            }
        }
    }
})
