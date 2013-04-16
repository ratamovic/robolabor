package com.codexperiments.robolabor.task;

public interface TaskRuleMapping<TType> {
    Class<TType> getTargetType();
    
    Object getTargetId(TType pTarget);
}
