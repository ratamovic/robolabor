package com.codexperiments.robolabor.task.android;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskResult;

public class TaskManagerAndroidException extends RuntimeException {
    private static final long serialVersionUID = 1075178581665280357L;

    public TaskManagerAndroidException(String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments));
    }

    public TaskManagerAndroidException(Throwable pThrowable, String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments), pThrowable);
    }

    public static TaskManagerAndroidException emitterIdCouldNotBeDetermined(TaskResult<?> pTask) {
        return new TaskManagerAndroidException("Invalid task %1$s : Emitter Id couldn't be bound.", pTask);
    }

    public static TaskManagerAndroidException emitterNotManaged(Object pEmitterId, Object pEmitter) {
        return new TaskManagerAndroidException("A call to manage for emitter %2$s with Id %1$s is missing.", pEmitterId, pEmitter);
    }

    public static TaskManagerAndroidException innerTasksNotAllowed(Task<?> pTask) {
        return new TaskManagerAndroidException("Inner tasks like %1$s not allowed by configuration.", pTask.getClass());
    }

    public static TaskManagerAndroidException internalError() {
        return internalError(null);
    }

    public static TaskManagerAndroidException internalError(Throwable pThrowable) {
        return new TaskManagerAndroidException(pThrowable, "Internal error inside the TaskManager.");
    }

    public static TaskManagerAndroidException invalidEmitterId(Object pEmitterId, Object pEmitter) {
        return new TaskManagerAndroidException("Emitter Id %1$s is invalid for emitter %2$s.", pEmitterId, pEmitter);
    }

    public static TaskManagerAndroidException mustBeExecutedFromUIThread() {
        return new TaskManagerAndroidException("This method must be executed from the UI-Thread only.");
    }

    public static TaskManagerAndroidException notCalledFromTask() {
        return new TaskManagerAndroidException("This operation must be called inside a task.");
    }

    public static TaskManagerAndroidException progressCalledAfterTaskFinished() {
        return new TaskManagerAndroidException("notifyProgress() called after task finished.");
    }

    public static TaskManagerAndroidException taskExecutedFromUnexecutedTask(Object pEmitter) {
        return new TaskManagerAndroidException("Task executed from parent task %1$s that hasn't been executed yet.", pEmitter);
    }

    public static TaskManagerAndroidException unmanagedEmittersNotAllowed(Object pEmitter) {
        return new TaskManagerAndroidException("Unmanaged emitter forbidden by configuration (%1$s).", pEmitter);
    }
}
