package com.codexperiments.robolabor.task.handler;

import com.codexperiments.robolabor.task.id.TaskId;

public interface TaskIdentifiable
{
    /**
     * Identity of a task. Two tasks are considered to be the same if they their Id object are equals. Only the equals() method is
     * called. The Id object returned will be compared to the Id of other tasks so that TaskManager can detect duplicated tasks
     * and avoid executing them.
     * 
     * Note that equals method on the task itself is used as a last resort. So implementing TaskIdentity is not compulsory. This
     * interface is provided to ease Identity check implementation, since overriding equals() and hashCode() can be quite tedious.
     * Here, you only have to return a simple object that already implements these methods (e.g. an Integer).
     * 
     * @return Identity of the Object. Must not be null.
     */
    TaskId getId();
}
