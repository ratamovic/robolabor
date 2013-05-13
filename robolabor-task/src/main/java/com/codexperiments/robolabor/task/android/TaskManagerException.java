package com.codexperiments.robolabor.task.android;

import com.codexperiments.robolabor.task.Task;

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

    public static TaskManagerException internalError()
    {
        return new TaskManagerException("Internal error inside the TaskManager");
    }

    public static TaskManagerException invalidTask()
    {
        return new TaskManagerException("Invalid task : null value");
    }

    public static TaskManagerException invalidTask(Task<?> pTask, String pComplement)
    {
        return new TaskManagerException("Invalid task %1$s : %2$s", pTask, pComplement);
    }

    public static TaskManagerException mustBeExecutedFromUIThread()
    {
        return new TaskManagerException("This method must be executed from the UI-Thread only");
    }

    public static TaskManagerException notCalledFromTask()
    {
        return new TaskManagerException("This operation must be called inside a task.");
    }
}
