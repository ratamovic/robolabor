package com.codexperiments.robolabor.task;

public interface TaskManager
{
    void manage(Object pEmitter);

    void unmanage(Object pEmitter);

    void execute(Task<?> pTask);

    boolean listen(TaskResult<?> pTaskResult);

    void notifyProgress(TaskProgress pProgress);
}
