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

package batect.ui

import java.io.InputStream

class Prompt(
    private val console: Console,
    private val stdin: InputStream
) {
    fun askYesNoQuestion(questionText: String): YesNoAnswer {
        val reader = stdin.bufferedReader(Charsets.UTF_8)
        var firstTime = true

        while (true) {
            if (firstTime) {
                console.print("$questionText (Y/n) ")
                firstTime = false
            } else {
                console.print("Please enter 'y' or 'n': ")
            }

            val response = reader.readLine()

            when (response.toLowerCase().trim()) {
                "y", "yes", "" -> return YesNoAnswer.Yes
                "n", "no" -> return YesNoAnswer.No
            }
        }
    }
}

enum class YesNoAnswer {
    Yes,
    No
}
