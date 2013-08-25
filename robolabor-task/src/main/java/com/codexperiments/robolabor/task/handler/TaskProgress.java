package com.codexperiments.robolabor.task.handler;

/**
 * TODO Pass a progression object.
 */
public interface TaskProgress<TProgress> extends TaskHandler {
    /**
     * Handler method called when task computation notifies of some progress. This method is called on the UI Thread. This is
     * where objects or components related to the UI should be updated (e.g. increasing a progression bar). It is safe to call any
     * outer object from here. If the latter is not reachable, then progression notifications are ignored by the TaskManager and
     * thus this handler don't get called. If reference to outer objects get restored at some point during task execution, then
     * this handler will process again any new progress notifications.
     * 
     * @param pTaskManager Use this TaskManager to perform any operation from the handler.
     */
    void onProgress(TProgress pProgress);
}
