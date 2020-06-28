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

package batect.testutils

import batect.execution.model.events.TaskEvent
import batect.execution.model.events.data
import batect.execution.model.rules.TaskStepRule
import batect.execution.model.rules.data
import batect.execution.model.steps.TaskStep
import batect.execution.model.steps.data
import batect.logging.Logger
import batect.testutils.logging.InMemoryLogSink
import batect.utils.Json
import org.spekframework.spek2.dsl.LifecycleAware

fun logRepresentationOf(step: TaskStep): String {
    val sink = InMemoryLogSink(Json.forLogging)
    val logger = Logger("test logger", sink)

    logger.info {
        data("step", step)
    }

    return sink.loggedMessages.single().additionalData.getValue("step").toJSON(sink.writer.json).toString()
}

fun logRepresentationOf(rule: TaskStepRule): String {
    val sink = InMemoryLogSink(Json.forLogging)
    val logger = Logger("test logger", sink)

    logger.info {
        data("rule", rule)
    }

    return sink.loggedMessages.single().additionalData.getValue("rule").toJSON(sink.writer.json).toString()
}

fun logRepresentationOf(event: TaskEvent): String {
    val sink = InMemoryLogSink(Json.forLogging)
    val logger = Logger("test logger", sink)

    logger.info {
        data("event", event)
    }

    return sink.loggedMessages.single().additionalData.getValue("event").toJSON(sink.writer.json).toString()
}

fun LifecycleAware.createLoggerForEachTest() = createForEachTest { Logger("test logger", InMemoryLogSink(Json.forLogging)) }
