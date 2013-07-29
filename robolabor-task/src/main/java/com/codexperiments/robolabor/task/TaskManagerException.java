package com.codexperiments.robolabor.task;

public class TaskManagerException extends RuntimeException {
    private static final long serialVersionUID = -4615749565432900659L;

    public TaskManagerException(String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments));
    }

    public TaskManagerException(Throwable pThrowable, String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments), pThrowable);
    }
}
