package batect

import batect.model.TaskStateMachine
import batect.model.events.TaskEvent
import batect.model.events.TaskEventSink
import batect.model.steps.FinishTaskStep
import batect.model.steps.TaskStep
import batect.model.steps.TaskStepRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

// Why do we do all this when a ThreadPoolExecutor would provide us with a 'maximum concurrent threads' limit?
// Because it requires us to just feed it tasks and it internally will manage the queue of work.
// However, we want to manage the queue of work ourselves (this is what the state machine and the events do), so
// that we can retract work (steps) that have not been started yet in the event of a failure. This retraction is
// all handled by the state machine, so all we need to do is only submit work to the thread pool when we there are
// fewer threads running than our maximum concurrent threads limit.
class ParallelExecutionManager(
        private val eventLogger: EventLogger,
        private val taskStepRunner: TaskStepRunner,
        private val stateMachine: TaskStateMachine,
        private val taskName: String,
        private val maximumConcurrentSteps: Int
) {
    private val eventSink = createEventSink()
    private val threadPool = createThreadPool()

    private var exitCode: Int? = null
    private val startNewWorkLockObject = Object()
    private val finishedSignal = CountDownLatch(1)
    private var runningSteps = 0

    fun run(): Int {
        exitCode = null

        startNewWorkIfPossible()

        finishedSignal.await()
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

        if (exitCode != null) {
            return exitCode!!
        } else {
            eventLogger.logTaskFailed(taskName)
            return -1
        }
    }

    private fun createThreadPool() =
            object : ThreadPoolExecutor(maximumConcurrentSteps, maximumConcurrentSteps, 0, TimeUnit.NANOSECONDS, LinkedBlockingQueue<Runnable>()) {
                override fun afterExecute(r: Runnable?, t: Throwable?) {
                    super.afterExecute(r, t)
                    afterStepFinished()
                }
            }

    private fun createEventSink() = object : TaskEventSink {
        override fun postEvent(event: TaskEvent) {
            eventLogger.postEvent(event)
            stateMachine.postEvent(event)
            startNewWorkIfPossible()
        }
    }

    private fun startNewWorkIfPossible(justCompletedStep: Boolean = false) {
        synchronized(startNewWorkLockObject) {
            if (justCompletedStep) {
                runningSteps--
            }

            while (runningSteps < maximumConcurrentSteps) {
                val step = stateMachine.popNextStep()

                if (step == null) {
                    break
                }

                runningSteps++
                runStep(step, threadPool, eventSink)
            }

            if (runningSteps == 0) {
                finishedSignal.countDown()
            }
        }
    }

    private fun runStep(step: TaskStep, threadPool: ThreadPoolExecutor, eventSink: TaskEventSink) {
        threadPool.execute {
            eventLogger.logBeforeStartingStep(step)
            taskStepRunner.run(step, eventSink)

            if (step is FinishTaskStep) {
                exitCode = step.exitCode
            }
        }
    }

    private fun afterStepFinished() {
        startNewWorkIfPossible(justCompletedStep = true)
    }
}
