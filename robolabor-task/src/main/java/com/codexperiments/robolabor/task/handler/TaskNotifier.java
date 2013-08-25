package com.codexperiments.robolabor.task.handler;

/**
 * TODO Pass a progression object.
 */
public interface TaskNotifier<TProgress> {
    void notifyProgress(TProgress pProgress);
}
