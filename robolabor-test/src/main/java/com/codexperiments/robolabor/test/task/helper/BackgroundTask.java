package com.codexperiments.robolabor.test.task.helper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.util.ProgressiveTask;

public class BackgroundTask implements ProgressiveTask<Integer>
{
    private static final int TASK_DURATION = 5000;
    private static final int TASK_TIMEOUT = 10000;

    private Boolean mCheckEmitterNull;
    private CountDownLatch mTaskFinished;
    private Integer mTaskResult;
    private Throwable mTaskException;

    public BackgroundTask(Integer pTaskResult)
    {
        super();
        mCheckEmitterNull = null;
        mTaskFinished = new CountDownLatch(1);
        mTaskResult = pTaskResult;
        mTaskException = null;
    }

    public BackgroundTask(Integer pTaskResult, Boolean pCheckEmitterNull)
    {
        super();
        mCheckEmitterNull = pCheckEmitterNull;
        mTaskFinished = new CountDownLatch(1);
        mTaskResult = pTaskResult;
        mTaskException = null;
    }

    public Integer onProcess(TaskManager pTaskManager) throws Exception
    {
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure task is executed only once.
        pTaskManager.notifyProgress(this);
        Thread.sleep(TASK_DURATION);
        return mTaskResult;
    }

    public void onProgress(TaskManager pTaskManager)
    {
    }

    public void onFinish(TaskManager pTaskManager, Integer pTaskResult)
    {
        // Check if outer object reference has been restored (or not).
        if (mCheckEmitterNull != null) {
            if (mCheckEmitterNull) {
                assertThat(getEmitter(), nullValue());
            } else {
                assertThat(getEmitter(), not(nullValue()));
            }
        }

        // Save result.
        mTaskResult = pTaskResult;
        if (getEmitter() != null) {
            setResult(mTaskResult, null);
        }

        // Notify listeners that task execution is finished.
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure termination handler is executed only once.
        mTaskFinished.countDown();
    }

    public void onFail(TaskManager pTaskManager, Throwable pTaskException)
    {
        mTaskException = pTaskException;
        setResult(null, mTaskException);

        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure termination handler is executed only once.
        mTaskFinished.countDown();
    }

    public Integer getTaskResult()
    {
        return mTaskResult;
    }

    public Throwable getTaskException()
    {
        return mTaskException;
    }

    public void reset(Integer pTaskResult)
    {
        mTaskFinished = new CountDownLatch(1);
        mTaskResult = pTaskResult;
    }

    public boolean await()
    {
        try {
            return mTaskFinished.await(TASK_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException eInterruptedException) {
            fail();
            return false;
        }
    }

    /**
     * Override in child classes to handle "inner tasks".
     * 
     * @return Task emitter (i.e. the outer class containing the task).
     */
    public Object getEmitter()
    {
        return null;
    }

    @Deprecated
    public void setResult(Integer pTaskResult, Throwable pTaskException)
    {
    }
}
