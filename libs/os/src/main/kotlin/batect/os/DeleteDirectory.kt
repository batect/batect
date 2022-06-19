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

package batect.os

import java.nio.file.Files
import java.nio.file.Path

fun deleteDirectory(directory: Path) {
    if (!Files.exists(directory)) {
        return
    }

    deleteDirectoryContents(directory)
    deletePossiblyReadOnlyPath(directory)
}

fun deleteDirectoryContents(directory: Path) {
    if (!Files.exists(directory)) {
        return
    }

    Files.newDirectoryStream(directory).use { stream ->
        stream.forEach { path ->
            if (Files.isDirectory(path)) {
                deleteDirectoryContents(path)
            }

            deletePossiblyReadOnlyPath(path)
        }
    }
}

fun deletePossiblyReadOnlyPath(path: Path) {
    unsetReadOnly(path)
    Files.delete(path)
}

fun unsetReadOnly(path: Path) {
    if (path.fileSystem.supportedFileAttributeViews().contains("dos")) {
        Files.setAttribute(path, "dos:readonly", false)
    }
}
