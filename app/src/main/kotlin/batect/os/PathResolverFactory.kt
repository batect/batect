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

package batect.os

import java.nio.file.FileSystem
import java.nio.file.Path

class PathResolverFactory(private val fileSystem: FileSystem) {
    fun createResolver(relativeTo: Path): PathResolver = PathResolver(relativeTo)
    fun createResolverForCurrentDirectory(): PathResolver = PathResolver(fileSystem.getPath("."))
}
