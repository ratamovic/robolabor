package com.codexperiments.robolabor.task;

public interface TaskManager
{
    void manage(Object pEmitter);
    
    void unmanage(Object pEmitter);
    
    <TResult> void execute(Task<TResult> pTask);

    <TResult> boolean listen(TaskResult<TResult> pTaskListener);

    void notifyProgress(TaskProgress<?> pProgress);
}
