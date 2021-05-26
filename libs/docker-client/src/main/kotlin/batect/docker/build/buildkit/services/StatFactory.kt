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

package batect.docker.build.buildkit.services

import fsutil.types.Stat
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

// On Windows:
// - files' and directories' permission bits are always rwxr-xr-x regardless of file system permissions (other mode bits such as type are preserved)
// - uid, gid, devmajor and devminor are always 0
// - xattrs is always empty

class StatFactory {
    fun createStat(path: Path, relativePath: String, attrs: BasicFileAttributes): Stat {
        val modTime = attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS)

        return Stat(
            relativePath,
            "0755".toInt(8),
            123,
            456,
            attrs.size(),
            modTime
        )
    }
}
