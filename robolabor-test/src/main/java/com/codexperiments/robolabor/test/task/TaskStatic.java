package com.codexperiments.robolabor.test.task;

import java.util.concurrent.CountDownLatch;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.util.ProgressiveTask;

public class TaskStatic
{
    private static final int TASK_DURATION = 5000;

    private TaskManager mTaskManager;
    private StaticTask mTask;

    public TaskStatic(TaskManager pTaskManager)
    {
        super();
        mTaskManager = pTaskManager;
        mTask = null;
    }

    public CountDownLatch runTask(final Integer pTaskResult)
    {
        mTask = new StaticTask(pTaskResult);
        mTaskManager.execute(mTask);
        return mTask.mTaskFinished;
    }

    public Integer getTaskResult()
    {
        return mTask.mTaskResult;
    }

    public Throwable getTaskException()
    {
        return mTask.mTaskException;
    }

    private static class StaticTask implements ProgressiveTask<Integer>
    {
        private Integer mTaskResult;
        private Throwable mTaskException;
        private CountDownLatch mTaskFinished;

        public StaticTask(Integer pTaskResult)
        {
            super();
            mTaskResult = pTaskResult;
            mTaskException = null;
            mTaskFinished = new CountDownLatch(1);
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
            mTaskResult = pTaskResult;
            mTaskFinished.countDown();
        }

        public void onFail(TaskManager pTaskManager, Throwable pThrowable)
        {
            mTaskException = pThrowable;
        }
    }
}
