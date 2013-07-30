package com.codexperiments.robolabor.test.task.helper;

import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.test.common.TestApplicationContext;

public class TaskEmitter {
    private boolean mCheckEmitterNull;
    private boolean mStepByStep;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;

    private Handler mUIThread;

    public static TaskEmitter persisting(TestApplicationContext pApplicationContext) {
        return new TaskEmitter(false, false, pApplicationContext);
    }

    public static TaskEmitter destroyed(TestApplicationContext pApplicationContext) {
        return new TaskEmitter(true, false, pApplicationContext);
    }

    public static TaskEmitter stepByStep(TestApplicationContext pApplicationContext) {
        return new TaskEmitter(false, true, pApplicationContext);
    }

    public static TaskEmitter stepByStepDestroyed(TestApplicationContext pApplicationContext) {
        return new TaskEmitter(true, true, pApplicationContext);
    }

    public TaskEmitter(boolean pCheckEmitterNull, boolean pStepByStep, TestApplicationContext pApplicationContext) {
        super();
        mCheckEmitterNull = pCheckEmitterNull;
        mStepByStep = pStepByStep;
        mTaskManager = pApplicationContext.getManager(TaskManager.class);

        mTaskResult = null;
        mTaskException = null;

        mUIThread = new Handler(Looper.getMainLooper());
    }

    public void start() {
        mTaskManager.manage(this);
    }

    public void stop() {
        mTaskManager.unmanage(this);
    }

    public BackgroundTask runInnerTask(final Integer pTaskResult) {
        final BackgroundTask lBackgroundTask = new InnerBackgroundTask(pTaskResult, mCheckEmitterNull, mStepByStep);
        mUIThread.post(new Runnable() {
            public void run() {
                mTaskManager.execute(lBackgroundTask);
            }
        });
        return lBackgroundTask;
    }

    public Integer getTaskResult() {
        return mTaskResult;
    }

    public Throwable getTaskException() {
        return mTaskException;
    }

    private class InnerBackgroundTask extends BackgroundTask {
        public InnerBackgroundTask(Integer pTaskResult, Boolean pCheckOwnerIsNull, boolean pStepByStep) {
            super(pTaskResult, pCheckOwnerIsNull, pStepByStep);
        }

        @Override
        public Object getEmitter() {
            return TaskEmitter.this;
        }

        @Override
        public void onFinish(Integer pTaskResult) {
            if (getEmitter() != null) {
                mTaskResult = pTaskResult;
            }
            super.onFinish(pTaskResult);
        }

        @Override
        public void onFail(Throwable pTaskException) {
            if (getEmitter() != null) {
                mTaskException = pTaskException;
            }
            super.onFail(pTaskException);
        }
    }
}
