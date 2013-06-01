package com.codexperiments.robolabor.test.task.helper;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskResult;
import com.codexperiments.robolabor.task.id.TaskRef;

public class BackgroundTaskResult implements TaskResult<Integer>
{
    private TaskRef<Integer> mTaskRef;
    private Boolean mCheckEmitterNull;
    private Integer mTaskResult;
    private Throwable mTaskException;

    private CountDownLatch mTaskFinished;

    public BackgroundTaskResult()
    {
        this(null);
    }

    public BackgroundTaskResult(Boolean pCheckEmitterNull)
    {
        super();

        mCheckEmitterNull = pCheckEmitterNull;
        mTaskResult = null;
        mTaskException = null;

        mTaskFinished = new CountDownLatch(1);
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
        // Notify listeners that task execution is finished.
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure termination handler is executed only once.
        mTaskFinished.countDown();
    }

    public void onFail(TaskManager pTaskManager, Throwable pTaskException)
    {
        mTaskException = pTaskException;
        assertThat(mTaskFinished.getCount(), equalTo(1l)); // Ensure termination handler is executed only once.
        mTaskFinished.countDown();
    }

    public boolean awaitFinished()
    {
        try {
            return mTaskFinished.await(BackgroundTask.TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException eInterruptedException) {
            fail();
            return false;
        }
    }

    public Integer getTaskResult()
    {
        return mTaskResult;
    }

    public Throwable getTaskException()
    {
        return mTaskException;
    }

    protected Boolean getCheckEmitterNull()
    {
        return mCheckEmitterNull;
    }

    public TaskRef<Integer> getTaskRef()
    {
        return mTaskRef;
    }

    public void setTaskId(TaskRef<Integer> pTaskRef)
    {
        mTaskRef = pTaskRef;
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

    @Override
    public String toString()
    {
        return "BackgroundTask [mTaskResult=" + mTaskResult + ", mTaskException=" + mTaskException + "]";
    }
}
