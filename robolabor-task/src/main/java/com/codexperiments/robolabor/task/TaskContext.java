package com.codexperiments.robolabor.task;

public interface TaskContext
{
    boolean unmap(Task<?> pTask);

    boolean map(Task<?> pTask);
}
