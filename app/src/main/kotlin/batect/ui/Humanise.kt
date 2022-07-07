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

package batect.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

fun Duration.humanise(): String = when {
    isZero -> "0s"
    seconds < 30 -> {
        val durationDisplay = BigDecimal.valueOf(seconds)
            .add(BigDecimal.valueOf(nano.toLong(), 9))
            .setScale(1, RoundingMode.HALF_UP)

        "${durationDisplay}s"
    }
    seconds < 60 -> "${wholeSeconds}s"
    toMinutes() < 60 -> "${wholeMinutes}m ${wholeSeconds}s"
    else -> "${toHours()}h ${wholeMinutes}m ${wholeSeconds}s"
}

private val Duration.wholeSeconds: BigDecimal
    get() {
        return BigDecimal.valueOf(seconds - (60 * toMinutes()))
            .add(BigDecimal.valueOf(nano.toLong(), 9))
            .setScale(0, RoundingMode.HALF_UP)
    }

private val Duration.wholeMinutes: Long
    get() = toMinutes() - (60 * toHours())
