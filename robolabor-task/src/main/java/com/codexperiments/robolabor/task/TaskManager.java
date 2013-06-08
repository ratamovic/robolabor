package com.codexperiments.robolabor.task;

import com.codexperiments.robolabor.task.id.TaskRef;

public interface TaskManager
{
    void manage(Object pEmitter);

    void unmanage(Object pEmitter);

    <TResult> TaskRef<TResult> execute(Task<TResult> pTask);

    <TResult> TaskRef<TResult> execute(Task<TResult> pTask, TaskResult<TResult> pTaskResult);

    <TResult> boolean rebind(TaskRef<TResult> pTaskId, TaskResult<TResult> pTaskResult);

    void notifyProgress(TaskProgress pProgress);
}
