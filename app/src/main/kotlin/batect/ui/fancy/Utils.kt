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

package batect.ui.fancy

import batect.ui.text.Text
import batect.ui.text.TextRun

internal fun humanReadableList(list: Collection<Text>): TextRun {
    return list.foldIndexed(TextRun()) { index, acc, current ->
        val secondLastItem = index == list.size - 2
        val beforeSecondLastItem = index < list.size - 2

        val separator = when {
            secondLastItem -> Text(" and ")
            beforeSecondLastItem -> Text(", ")
            else -> Text("")
        }

        acc + current + separator
    }
}
