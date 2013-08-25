package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskProgress;

public interface IdentifiableProgressTask<TParam, TProgress, TResult>
    extends Task<TParam, TProgress, TResult>, TaskIdentifiable, TaskProgress<TProgress>
{
}
