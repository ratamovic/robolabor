package com.codexperiments.robolabor.test.task.helper;

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
        mTaskResult = pTaskResult;
        if (getEmitter() != null) {
            setResult(mTaskResult, null);
        }
        mTaskFinished.countDown();
    }

    public void onFail(TaskManager pTaskManager, Throwable pTaskException)
    {
        mTaskException = pTaskException;
        setResult(null, mTaskException);
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

    public boolean await()
    {
        try {
            return mTaskFinished.await(TASK_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException eInterruptedException) {
            fail();
            return false;
        }
    }

    public Object getEmitter()
    {
        return null;
    }

    public void setResult(Integer pTaskResult, Throwable pTaskException)
    {
    }
}
