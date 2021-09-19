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
import batect.wrapper.WrapperCacheCleanupTask

class BackgroundTaskManager(
    private val wrapperCacheCleanupTask: WrapperCacheCleanupTask,
    private val gitRepositoryCacheCleanupTask: GitRepositoryCacheCleanupTask,
    private val telemetryUploadTask: TelemetryUploadTask
) {
    fun startBackgroundTasks() {
        wrapperCacheCleanupTask.start()
        gitRepositoryCacheCleanupTask.start()
        telemetryUploadTask.start()
    }
}
