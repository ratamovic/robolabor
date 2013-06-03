package com.codexperiments.robolabor.task.android;

import com.codexperiments.robolabor.task.TaskResult;

/**
 * Indicates a case that should never happen and which indicates a programming or configuration error (e.g. a reflection call
 * which fails), a default case that should never happen in a switch, etc.
 */
public class TaskManagerException extends RuntimeException
{
    private static final long serialVersionUID = -4615749565432900659L;

    protected TaskManagerException(String pMessage, Object... pArguments)
    {
        super(String.format(pMessage, pArguments));
    }

    protected TaskManagerException(Throwable pThrowable, String pMessage, Object... pArguments)
    {
        super(String.format(pMessage, pArguments), pThrowable);
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

    public static TaskManagerException emitterIdCouldNotBeDetermined(TaskResult<?> pTask)
    {
        return new TaskManagerException("Invalid task %1$s : Emitter Id couldn't be bound.", pTask);
    }

    public static TaskManagerException mustBeExecutedFromUIThread()
    {
        return new TaskManagerException("This method must be executed from the UI-Thread only.");
    }

    public static TaskManagerException notCalledFromTask()
    {
        return new TaskManagerException("This operation must be called inside a task.");
    }
}
