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

package batect.execution

import batect.config.Configuration
import batect.utils.EditDistanceCalculator

class TaskSuggester {
    fun suggestCorrections(config: Configuration, originalTaskName: String): List<String> {
        val taskNames = config.tasks.keys
        val distances = taskNames.associateWith { EditDistanceCalculator.calculateDistanceBetween(originalTaskName, it) }

        return distances
            .filterValues { it <= 3 }
            .toSortedMap(Comparator { o1, o2 -> distances.getValue(o1).compareTo(distances.getValue(o2)) })
            .keys
            .toList()
    }
}
