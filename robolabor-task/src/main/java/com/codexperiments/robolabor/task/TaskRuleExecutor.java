package com.codexperiments.robolabor.task;

public interface TaskRuleExecutor<TType extends Task<?>> {
    Class<TType> getTaskType();
    
    TaskConfiguration getConfiguration(TType pTask);
}
