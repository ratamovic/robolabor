package com.codexperiments.robolabor.task.android;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskResult;

public class AndroidTaskManagerException extends RuntimeException {
    private static final long serialVersionUID = 1075178581665280357L;

    public AndroidTaskManagerException(String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments));
    }

    public AndroidTaskManagerException(Throwable pThrowable, String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments), pThrowable);
    }

    public static AndroidTaskManagerException emitterIdCouldNotBeDetermined(TaskResult<?> pTask) {
        return new AndroidTaskManagerException("Invalid task %1$s : Emitter Id couldn't be bound.", pTask);
    }

    public static AndroidTaskManagerException emitterNotManaged(Object pEmitterId, Object pEmitter) {
        return new AndroidTaskManagerException("A call to manage for emitter %2$s with Id %1$s is missing.", pEmitterId, pEmitter);
    }

    public static AndroidTaskManagerException innerTasksNotAllowed(Task<?, ?, ?> pTask) {
        return new AndroidTaskManagerException("Inner tasks like %1$s not allowed by configuration.", pTask.getClass());
    }

    public static AndroidTaskManagerException internalError() {
        return internalError(null);
    }

    public static AndroidTaskManagerException internalError(Throwable pThrowable) {
        return new AndroidTaskManagerException(pThrowable, "Internal error inside the TaskManager.");
    }

    public static AndroidTaskManagerException invalidEmitterId(Object pEmitterId, Object pEmitter) {
        return new AndroidTaskManagerException("Emitter Id %1$s is invalid for emitter %2$s.", pEmitterId, pEmitter);
    }

    public static AndroidTaskManagerException mustBeExecutedFromUIThread() {
        return new AndroidTaskManagerException("This method must be executed from the UI-Thread only.");
    }

    public static AndroidTaskManagerException notCalledFromTask() {
        return new AndroidTaskManagerException("This operation must be called inside a task.");
    }

    public static AndroidTaskManagerException progressCalledAfterTaskFinished() {
        return new AndroidTaskManagerException("notifyProgress() called after task finished.");
    }

    public static AndroidTaskManagerException taskExecutedFromUnexecutedTask(Object pEmitter) {
        return new AndroidTaskManagerException("Task executed from parent task %1$s that hasn't been executed yet.", pEmitter);
    }

    public static AndroidTaskManagerException unmanagedEmittersNotAllowed(Object pEmitter) {
        return new AndroidTaskManagerException("Unmanaged emitter forbidden by configuration (%1$s).", pEmitter);
    }
}
