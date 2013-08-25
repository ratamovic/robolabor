package com.codexperiments.robolabor.task.handler;

public interface Task<TParam, TProgress, TResult> extends TaskResult<TResult> {
    /**
     * Execute the background task. The following points should be imperatively respected:
     * <ul>
     * <li>Don't modify any UI related objects here. UI objects should be updated from the UI Thread only. Use TaskProgress for
     * that purpose.</li>
     * <li>Don't try to access the outer object from here if task is defined as an inner or anonymous class. Indeed, reference to
     * any outer object is removed during processing (but restored when progress or termination handlers are called).</li>
     * <li>Use the give TaskManager to perform any task operation during processing.</li>
     * </ul>
     * 
     * @param pTaskManager Use this TaskManager to perform any operation from the handler.
     * 
     * @throws Exception If any exception occurs during processing. The exception is forwarded to TaskResult.onFail() if defined.
     */
    TResult onProcess(TParam pParam, TaskNotifier<TProgress> pNotifier) throws Exception;
}
