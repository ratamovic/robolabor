package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.mustBeExecutedFromUIThread;

import com.codexperiments.robolabor.task.TaskScheduler;

import android.os.Handler;
import android.os.Looper;

public class TaskSchedulerAndroidUI implements TaskScheduler {
    private Handler mUIQueue;
    private Looper mUILooper;

    public TaskSchedulerAndroidUI() {
        super();
        mUILooper = Looper.getMainLooper();
        mUIQueue = new Handler(mUILooper);
    }

    @Override
    public void checkCurrentThread() {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
    }

    @Override
    public void scheduleCallback(Runnable pCallbackRunnable) {
        mUIQueue.post(pCallbackRunnable);
    }

    @Override
    public void scheduleCallbackIfNecessary(Runnable pCallbackRunnable) {
        if (Looper.myLooper() == mUILooper) {
            pCallbackRunnable.run();
        } else {
            mUIQueue.post(pCallbackRunnable);
        }
    }
}
