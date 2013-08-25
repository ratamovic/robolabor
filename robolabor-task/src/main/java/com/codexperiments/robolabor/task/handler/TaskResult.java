package com.codexperiments.robolabor.task.handler;

public interface TaskResult<TResult> extends TaskHandler {
    /**
     * Handler method called when task computation has finished correctly. This method is called on the UI Thread. This is where
     * objects or components related to the UI should be updated (e.g. merging data with existing results on screens). It is safe
     * to call any outer class from here.
     * 
     * TODO Explain what happen if reference is not restored.
     * 
     * @param pTaskManager Use this TaskManager to perform any operation from the handler.
     */
    void onFinish(TResult pResult);

    /**
     * Handler method called when task computation has failed during execution or in the onFinish() handler. This method is called
     * on the UI Thread. This is where objects or components related to the UI should be updated (e.g. display of an error
     * message). The given exception is the one that cause the failure (i.e. there is no encaspulation). It is safe to call any
     * outer class from here.
     * 
     * TODO Explain what happen if reference is not restored.
     * 
     * @param pTaskManager Use this TaskManager to perform any operation from the handler.
     */
    void onFail(Throwable pException);
}
