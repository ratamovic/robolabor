package com.codexperiments.robolabor.task;

/**
 * Indicates a case that should never happen and which indicates a programming or configuration error (e.g. a reflection call
 * which fails), a default case that should never happen in a switch, etc.
 */
public class TaskManagerException extends RuntimeException
{
    private static final long serialVersionUID = -4615749565432900659L;

    public TaskManagerException(String pMessage, Object... pArguments)
    {
        super(String.format(pMessage, pArguments));
    }

    public TaskManagerException(Throwable pThrowable, String pMessage, Object... pArguments)
    {
        super(String.format(pMessage, pArguments), pThrowable);
    }
}
