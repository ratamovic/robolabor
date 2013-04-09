package com.codexperiments.robolabor.task;

public interface TaskManager
{
    void manage(Object pOwner);
    
    void unmanage(Object pOwner);
    
    <TResult> TaskBuilder<TResult> execute(Task<TResult> pTask);

    <TResult> boolean listenPending(TaskResult<TResult> pTaskListener);

    void notifyProgress(TaskProgress pProgress);
    
    interface TaskBuilder<TResult> {
        TaskBuilder<TResult> singleInstance(Object pId);
        
        TaskBuilder<TResult> dontKeepResult();
        
        TaskBuilder<TResult> keepResultOnHold();
        
//        TaskBuilder<TResult> bindToOwner();
        
        void inMainQueue();
        
        void inBackgroundQueue();
    }
}
