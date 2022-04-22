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
import java.util.concurrent.ConcurrentHashMap
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
    private val maximumLevelOfParallelism: Int?,
    private val logger: Logger
) : TaskEventSink {
    private val threadPool = createThreadPool()
    private val workManagementLock = Object()
    private val finishedSignal = CountDownLatch(1)
    private val runningSteps = ConcurrentHashMap.newKeySet<TaskStep>()

    fun run() {
        startNewWorkIfPossible()

        finishedSignal.await()

        logger.info { message("Shutting down thread pool.") }
        threadPool.shutdown()

        logger.info { message("Waiting for thread pool to terminate.") }
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

        logger.info { message("Thread pool terminated.") }
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

                startNewWorkIfPossible()
            }
        }

    override fun postEvent(event: TaskEvent) {
        eventLogger.postEvent(event)

        if (!event.isInformationalEvent) {
            stateMachine.postEvent(event)
            startNewWorkIfPossible()
        }
    }

    private fun startNewWorkIfPossible() {
        synchronized(workManagementLock) {
            try {
                while (canRunMoreSteps()) {
                    val stepsStillRunning = runningSteps.isNotEmpty()
                    val step = stateMachine.popNextStep(stepsStillRunning) ?: break

                    runStep(step, threadPool)
                }
            } catch (e: Throwable) {
                logger.error {
                    message("Could not schedule new work.")
                    exception(e)
                }

                telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = true)
                eventLogger.postEvent(ExecutionFailedEvent("Could not schedule new work: $e"))

                throw e
            } finally {
                if (runningSteps.isEmpty()) {
                    logger.info {
                        message("No running steps, signalling execution manager to stop.")
                    }

                    finishedSignal.countDown()
                }
            }
        }
    }

    private fun canRunMoreSteps(): Boolean {
        if (maximumLevelOfParallelism == null) {
            return true
        }

        val stepsThatCountAgainstParallelismCap = runningSteps.count { it.countsAgainstParallelismCap }

        return stepsThatCountAgainstParallelismCap < maximumLevelOfParallelism
    }

    private fun runStep(step: TaskStep, threadPool: ThreadPoolExecutor) {
        runningSteps.add(step)

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
            } finally {
                synchronized(workManagementLock) {
                    runningSteps.remove(step)
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
}
