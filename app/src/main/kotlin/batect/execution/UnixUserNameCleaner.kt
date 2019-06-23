/*
   Copyright 2017-2019 Charles Korn.

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

package batect.execution

class UnixUserNameCleaner {
    fun clean(userName: String): String {
        val cleanedUserNameBuilder = StringBuilder()

        userName.toLowerCase().forEachIndexed { index, char ->
            val isFirstCharacter = index == 0
            val isLastCharacter = index == userName.length - 1

            if (shouldInclude(char, isFirstCharacter, isLastCharacter)) {
                cleanedUserNameBuilder.append(char)
            }
        }

        val cleanedUserName = cleanedUserNameBuilder.toString().take(32)

        if (cleanedUserName.isEmpty()) {
            return "default-user-name"
        }

        return cleanedUserName
    }

    private fun shouldInclude(char: Char, isFirstCharacter: Boolean, isLastCharacter: Boolean): Boolean {
        if (char.isLetter()) {
            return true
        }

        if (!isFirstCharacter) {
            if (char.isDigit() || char == '-' || char == '_') {
                return true
            }

            if (char == '$' && isLastCharacter) {
                return true
            }
        }

        return false
    }
}
