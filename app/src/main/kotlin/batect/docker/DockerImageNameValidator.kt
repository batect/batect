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

package batect.docker

object DockerImageNameValidator {
    private const val separators = """(\.|_|__|-+)"""
    private const val nonSeparators = "([a-z0-9]+)"
    private val nameRegex = Regex("^$nonSeparators($separators$nonSeparators)*$")

    const val validNameDescription = "must contain only lowercase letters, digits, dashes (-), single consecutive periods (.) or one or two consecutive underscores (_), and must not start or end with dashes, periods or underscores"

    fun isValidImageName(name: String): Boolean = nameRegex.matches(name)
}
