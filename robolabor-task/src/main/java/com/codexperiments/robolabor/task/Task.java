package com.codexperiments.robolabor.task;

public interface Task<TResult> extends TaskResult<TResult>
{
    /**
     * Execute the background task. The following points should be imperatively respected: - DON'T modify any UI related objects
     * here. UI objects should be updated from the UI Thread only. Use TaskProgress for that purpose. - DON'T try to access the
     * outer class from here if task is defined as an inner or anonymous class. Indeed, reference to the outer class is removed
     * during processing (but restored when TaskResult handler is called).
     * 
     * @throws Exception If any exception occurs during processing. The exception is forwarded to TaskResult.onFail() if defined.
     */
    TResult onProcess(TaskManager pTaskManager) throws Exception;
}
