package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.TaskIdentifiable;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

public interface IdentifiableProgressTaskResult<TResult> extends TaskResult<TResult>, TaskIdentifiable, TaskProgress
{
}
