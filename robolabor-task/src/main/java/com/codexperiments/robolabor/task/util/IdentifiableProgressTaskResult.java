package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;

public interface IdentifiableProgressTaskResult<TResult> extends TaskResult<TResult>, TaskIdentifiable, TaskProgress
{
}
