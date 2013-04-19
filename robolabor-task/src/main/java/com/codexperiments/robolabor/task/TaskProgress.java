package com.codexperiments.robolabor.task;

public interface TaskProgress<TResult> extends Task<TResult>
{
    void onProgress(TaskManager pTaskManager);
}
