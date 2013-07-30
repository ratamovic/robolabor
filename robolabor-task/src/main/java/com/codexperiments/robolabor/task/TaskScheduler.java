package com.codexperiments.robolabor.task;

public interface TaskScheduler {

    void checkCurrentThread();

    void schedule(Runnable pRunnable);

    void scheduleIfNecessary(Runnable pRunnable);

}