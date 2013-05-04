package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskProgress;

public interface ProgressiveUniqueTask<TResult> extends Task<TResult>, TaskIdentity, TaskProgress
{
}
