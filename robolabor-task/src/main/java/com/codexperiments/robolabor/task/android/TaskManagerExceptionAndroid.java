package com.codexperiments.robolabor.task.android;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskManagerException;
import com.codexperiments.robolabor.task.TaskResult;

/**
 * Indicates a case that should never happen and which indicates a programming or configuration error (e.g. a reflection call
 * which fails), a default case that should never happen in a switch, etc.
 */
public class TaskManagerExceptionAndroid
{
    public static TaskManagerException emitterIdCouldNotBeDetermined(TaskResult<?> pTask)
    {
        return new TaskManagerException("Invalid task %1$s : Emitter Id couldn't be bound.", pTask);
    }

    public static TaskManagerException emitterNotManaged(Object pEmitterId, Object pEmitter)
    {
        return new TaskManagerException("A call to manage for emitter %2$s with Id %1$s is missing).", pEmitterId, pEmitter);
    }

    public static TaskManagerException innerTasksNotAllowed(Task<?> pTask)
    {
        return new TaskManagerException("Inner tasks not allowed by configuration (%1$s).", pTask.getClass());
    }

    public static TaskManagerException internalError()
    {
        return internalError(null);
    }

    public static TaskManagerException internalError(Throwable pThrowable)
    {
        return new TaskManagerException(pThrowable, "Internal error inside the TaskManager.");
    }

    public static TaskManagerException invalidEmitterId(Object pEmitterId, Object pEmitter)
    {
        return new TaskManagerException("Emitter Id %1$s is invalid for emitter %2$s.", pEmitterId, pEmitter);
    }

    public static TaskManagerException mustBeExecutedFromUIThread()
    {
        return new TaskManagerException("This method must be executed from the UI-Thread only.");
    }

    public static TaskManagerException notCalledFromTask()
    {
        return new TaskManagerException("This operation must be called inside a task.");
    }

    public static TaskManagerException serviceNotDeclaredInManifest()
    {
        return new TaskManagerException("Service hasn't been declared in manifest file.");
    }

    public static TaskManagerException unmanagedEmittersNotAllowed(Object pEmitter)
    {
        return new TaskManagerException("Unmanaged emitter forbidden by configuration (%1$s).", pEmitter);
    }
}
