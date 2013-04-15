package com.codexperiments.robolabor.task;

public interface TaskRule<TType> {
    Class<TType> getTargetType();
    
    Object getTargetId(TType pTarget);
}
