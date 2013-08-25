package com.codexperiments.robolabor.test.task.helper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.codexperiments.robolabor.task.TaskRef;
import com.codexperiments.robolabor.task.handler.TaskNotifier;
import com.codexperiments.robolabor.task.util.ProgressTask;

public class BackgroundTask implements ProgressTask<Integer, Integer, Integer> {
    public static final int TASK_STEP_COUNT = 5;
    public static final int TASK_STEP_DURATION_MS = 1000;
    // At least one test must wait until this delay has ended. So please avoid increasing it except for debugging purpose.
    public static final int TASK_TIMEOUT_MS = 10000;
    public static final int TASK_PROGRESS_TIMEOUT_MS = 2000;

    private TaskRef<Integer> mTaskRef;
    private Boolean mCheckEmitterNull;
    private boolean mStepByStep;
    private int mStepCounter;
    private int mProgressCounter;
    private Integer mExpectedTaskResult;
    private Exception mExpectedTaskException;
    private Integer mTaskResult;
    private Throwable mTaskException;

    private volatile boolean mAwaitFinished;
    private CyclicBarrier mTaskStepStart;
    private CountDownLatch mTaskStepProgress;
    private CountDownLatch mTaskStepEnd;
    private CountDownLatch mTaskFinished;

    public BackgroundTask(Integer pTaskResult, Boolean pCheckEmitterNull, boolean pStepByStep) {
        this(pTaskResult, null, pCheckEmitterNull, pStepByStep);

    }

    public BackgroundTask(Exception pTaskException, Boolean pCheckEmitterNull, boolean pStepByStep) {
        this(null, pTaskException, pCheckEmitterNull, pStepByStep);

    }

    public BackgroundTask(Integer pTaskResult, Exception pTaskException, Boolean pCheckEmitterNull, boolean pStepByStep) {
        super();

        mCheckEmitterNull = pCheckEmitterNull;
        mStepByStep = pStepByStep;
        mStepCounter = pStepByStep ? TASK_STEP_COUNT : 0;
        mProgressCounter = 0;
        mExpectedTaskResult = pTaskResult;
        mExpectedTaskException = pTaskException;
        mTaskResult = null;
        mTaskException = null;

        mAwaitFinished = !mStepByStep;
        mTaskStepStart = mStepByStep ? new CyclicBarrier(2) : null;
        mTaskStepProgress = mStepByStep ? new CountDownLatch(1) : null;
        mTaskStepEnd = mStepByStep ? new CountDownLatch(1) : null;
        mTaskFinished = new CountDownLatch(1);
    }

    @Override
    public Integer onProcess(Integer pParam, TaskNotifier<Integer> pNotifier) throws Exception {
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure task is executed only once.
        // We have two cases here: either we loop until step by step is over or until we have performed all iterations.
        // We know that we are in step by step mode if mStepByStep is true at the beginning of the loop.
        // for (int i = mStepByStep ? TASK_STEP_COUNT : 0; mStepByStep || (i < TASK_STEP_COUNT); ++i) {
        while (true) {
            awaitStart();
            if (mAwaitFinished && (mStepCounter >= TASK_STEP_COUNT)) break;

            Thread.sleep(TASK_STEP_DURATION_MS);
            ++mStepCounter;
            pNotifier.notifyProgress(mStepCounter);
            notifyEnded();
        }
        if (mExpectedTaskException == null) {
            return mExpectedTaskResult;
        } else {
            throw mExpectedTaskException;
        }
    }

    @Override
    public void onProgress(Integer pProgress) {
        ++mProgressCounter;
        if (mTaskStepProgress != null) {
            mTaskStepProgress.countDown();
        }
    }

    @Override
    public void onFinish(Integer pTaskResult) {
        // Check if outer object reference has been restored (or not).
        if (mCheckEmitterNull != null) {
            if (mCheckEmitterNull) {
                assertThat(getEmitter(), nullValue());
            } else {
                assertThat(getEmitter(), not(nullValue()));
            }
        }

        // Save result. TODO This is stupid since result is already initialized... Create a new variable.
        mTaskResult = pTaskResult;
        // Notify listeners that task execution is finished.
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure termination handler is executed only once.
        mTaskFinished.countDown();
    }

    @Override
    public void onFail(Throwable pTaskException) {
        mTaskException = pTaskException;
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure termination handler is executed only once.
        mTaskFinished.countDown();
    }

    private void awaitStart() {
        if (mTaskStepStart != null) {
            try {
                mTaskStepStart.await(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (mAwaitFinished) {
                    mTaskStepStart = null;
                } else {
                    mTaskStepStart.reset();
                }
            } catch (TimeoutException eTimeoutException) {
                fail();
            } catch (InterruptedException eInterruptedException) {
                fail();
            } catch (BrokenBarrierException eBrokenBarrierException) {
                fail();
            }
        }
    }

    private void notifyEnded() {
        if (mTaskStepStart != null) {
            mTaskStepEnd.countDown();
        }
    }

    /**
     * Works in step by step mode only.
     */
    public boolean awaitStepExecuted() {
        if (mTaskStepStart == null) return false;

        try {
            if (!(mTaskStepStart.await(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS) >= 0)) return false;
            boolean lResult = mTaskStepEnd.await(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            mTaskStepEnd = new CountDownLatch(1);
            return lResult;
        } catch (TimeoutException eTimeoutException) {
            return false;
        } catch (InterruptedException eInterruptedException) {
            fail();
            return false;
        } catch (BrokenBarrierException eBrokenBarrierException) {
            fail();
            return false;
        }
    }

    /**
     * Works in step by step mode only.
     */
    public boolean awaitFinished() {
        try {
            mAwaitFinished = true;
            if (mTaskStepStart != null) {
                if (!(mTaskStepStart.await(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS) >= 0)) return false;
            }
            return mTaskFinished.await(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException eTimeoutException) {
            return false;
        } catch (InterruptedException eInterruptedException) {
            fail();
            return false;
        } catch (BrokenBarrierException eBrokenBarrierException) {
            fail();
            return false;
        }
    }

    /**
     * Works in step by step mode only. You don't have to call it but if you call it once, call it for each step where some
     * progress is expected (and only these ones)! Always execute it after awaitStepExecuted() or after awaitStepFinished() to
     * avoid getting stuck...
     */
    public boolean awaitProgressExecuted() {
        try {
            boolean lResult = mTaskStepProgress.await(TASK_PROGRESS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            mTaskStepProgress = new CountDownLatch(1);
            return lResult;
        } catch (InterruptedException eInterruptedException) {
            fail();
            return false;
        }
    }

    public void reset() {
        mTaskFinished = new CountDownLatch(1);
    }

    public int getStepCounter() {
        return mStepCounter;
    }

    public int getProgressCounter() {
        return mProgressCounter;
    }

    public Integer getTaskResult() {
        return mTaskResult;
    }

    public Throwable getTaskException() {
        return mTaskException;
    }

    protected Boolean getCheckEmitterNull() {
        return mCheckEmitterNull;
    }

    protected Boolean getStepByStep() {
        return mStepByStep;
    }

    public TaskRef<Integer> getTaskRef() {
        return mTaskRef;
    }

    public void setTaskRef(TaskRef<Integer> pTaskRef) {
        mTaskRef = pTaskRef;
    }

    /**
     * Override in child classes to handle "inner tasks".
     * 
     * @return Task emitter (i.e. the outer class containing the task).
     */
    public Object getEmitter() {
        return null;
    }

    @Override
    public String toString() {
        return "BackgroundTask [mTaskResult=" + mTaskResult + ", mTaskException=" + mTaskException + ", mStepCounter="
                        + mStepCounter + ", mProgressCounter=" + mProgressCounter + "]";
    }
}
