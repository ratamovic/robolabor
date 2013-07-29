package com.codexperiments.robolabor.task;

public interface TaskScheduler {

    void checkCurrentThread();

    void scheduleCallback(Runnable pCallbackRunnable);

    void scheduleCallbackIfNecessary(Runnable pCallbackRunnable);

}