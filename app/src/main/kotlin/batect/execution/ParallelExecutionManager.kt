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

import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.TaskStep
import batect.execution.model.steps.TaskStepRunner
import batect.execution.model.steps.data
import batect.logging.Logger
import batect.primitives.CancellationException
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.addUnhandledExceptionEvent
import batect.ui.EventLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

// Why do we do all this when a ThreadPoolExecutor serves a similar purpose?
// Because it requires us to just feed it tasks and it internally will manage the queue of work.
// However, we want to manage the queue of work ourselves (this is what the state machine and the events do), so
// that we can retract work (steps) that have not been started yet in the event of a failure and trigger them when
// events occur. This is all handled by the state machine, so all we need to do is only submit work to the thread
// pool when it is ready.
class ParallelExecutionManager(
    private val eventLogger: EventLogger,
    private val taskStepRunner: TaskStepRunner,
    private val stateMachine: TaskStateMachine,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val logger: Logger
) : TaskEventSink {
    private val threadPool = createThreadPool()
    private val startNewWorkLockObject = Object()
    private val finishedSignal = CountDownLatch(1)
    private var runningSteps = 0

    fun run() {
        startNewWorkIfPossible()

        finishedSignal.await()
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    private fun createThreadPool() =
        object : ThreadPoolExecutor(Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE, TimeUnit.DAYS, LinkedBlockingQueue<Runnable>()) {
            override fun afterExecute(r: Runnable?, t: Throwable?) {
                super.afterExecute(r, t)

                if (t != null) {
                    logger.error {
                        message("Unhandled exception during task step execution that was not caught by runStep().")
                        exception(t)
                    }

                    telemetrySessionBuilder.addUnhandledExceptionEvent(t, isUserFacing = true)
                    postEvent(ExecutionFailedEvent(t.toString()))
                }

                afterStepFinished()
            }
        }

    override fun postEvent(event: TaskEvent) {
        eventLogger.postEvent(event)

        if (!event.isInformationalEvent) {
            stateMachine.postEvent(event)
            startNewWorkIfPossible()
        }
    }

    private fun startNewWorkIfPossible(justCompletedStep: Boolean = false) {
        synchronized(startNewWorkLockObject) {
            if (justCompletedStep) {
                runningSteps--
            }

            try {
                while (true) {
                    val stepsStillRunning = runningSteps > 0
                    val step = stateMachine.popNextStep(stepsStillRunning)

                    if (step == null) {
                        break
                    }

                    runningSteps++
                    runStep(step, threadPool)
                }
            } catch (e: Throwable) {
                logger.error {
                    message("Could not schedule new work.")
                    exception(e)
                }

                throw e
            } finally {
                if (runningSteps == 0) {
                    finishedSignal.countDown()
                }
            }
        }
    }

    private fun runStep(step: TaskStep, threadPool: ThreadPoolExecutor) {
        threadPool.execute {
            try {
                logger.info {
                    message("Running step.")
                    data("step", step)
                }

                eventLogger.postEvent(StepStartingEvent(step))
                taskStepRunner.run(step, this)

                logger.info {
                    message("Step completed.")
                    data("step", step)
                }
            } catch (t: Throwable) {
                when {
                    t is CancellationException -> logCancellationException(step, t)
                    t is ExecutionException && t.cause is CancellationException -> logCancellationException(step, t)
                    else -> {
                        logger.error {
                            message("Unhandled exception during task step execution.")
                            exception(t)
                            data("step", step)
                        }

                        telemetrySessionBuilder.addUnhandledExceptionEvent(t, isUserFacing = true)
                        postEvent(ExecutionFailedEvent("During execution of step of kind '${step::class.simpleName}': " + t.toString()))
                    }
                }
            }
        }
    }

    private fun logCancellationException(step: TaskStep, ex: Exception) {
        logger.info {
            message("Step was cancelled and threw an exception.")
            exception(ex)
            data("step", step)
        }
    }

    private fun afterStepFinished() {
        startNewWorkIfPossible(justCompletedStep = true)
    }
}
