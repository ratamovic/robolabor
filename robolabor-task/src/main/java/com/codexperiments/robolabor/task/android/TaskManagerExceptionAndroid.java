package com.codexperiments.robolabor.task.android;

import com.codexperiments.robolabor.task.TaskManagerException;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskResult;

public class TaskManagerExceptionAndroid extends TaskManagerException {
    private static final long serialVersionUID = 1075178581665280357L;

    public TaskManagerExceptionAndroid(String pMessage, Object... pArguments) {
        super(pMessage, pArguments);
    }

    public TaskManagerExceptionAndroid(Throwable pThrowable, String pMessage, Object... pArguments) {
        super(pThrowable, pMessage, pArguments);
    }

    public static TaskManagerExceptionAndroid emitterIdCouldNotBeDetermined(TaskResult<?> pTask) {
        return new TaskManagerExceptionAndroid("Invalid task %1$s : Emitter Id couldn't be bound.", pTask);
    }

    public static TaskManagerExceptionAndroid emitterNotManaged(Object pEmitterId, Object pEmitter) {
        return new TaskManagerExceptionAndroid("A call to manage for emitter %2$s with Id %1$s is missing.", pEmitterId, pEmitter);
    }

    public static TaskManagerExceptionAndroid innerTasksNotAllowed(Task<?> pTask) {
        return new TaskManagerExceptionAndroid("Inner tasks like %1$s not allowed by configuration.", pTask.getClass());
    }

    public static TaskManagerExceptionAndroid internalError() {
        return internalError(null);
    }

    public static TaskManagerExceptionAndroid internalError(Throwable pThrowable) {
        return new TaskManagerExceptionAndroid(pThrowable, "Internal error inside the TaskManager.");
    }

    public static TaskManagerExceptionAndroid invalidEmitterId(Object pEmitterId, Object pEmitter) {
        return new TaskManagerExceptionAndroid("Emitter Id %1$s is invalid for emitter %2$s.", pEmitterId, pEmitter);
    }

    public static TaskManagerExceptionAndroid mustBeExecutedFromUIThread() {
        return new TaskManagerExceptionAndroid("This method must be executed from the UI-Thread only.");
    }

    public static TaskManagerExceptionAndroid notCalledFromTask() {
        return new TaskManagerExceptionAndroid("This operation must be called inside a task.");
    }

    public static TaskManagerExceptionAndroid taskExecutedFromUnexecutedTask(Object pEmitter) {
        return new TaskManagerExceptionAndroid("Task executed from parent task %1$s that hasn't been executed yet.", pEmitter);
    }

    public static TaskManagerExceptionAndroid unmanagedEmittersNotAllowed(Object pEmitter) {
        return new TaskManagerExceptionAndroid("Unmanaged emitter forbidden by configuration (%1$s).", pEmitter);
    }
}
