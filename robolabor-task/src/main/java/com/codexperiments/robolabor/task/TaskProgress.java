package com.codexperiments.robolabor.task;

public interface TaskProgress<TResult>
{
    void onProgress(TaskManager pTaskManager);
}
