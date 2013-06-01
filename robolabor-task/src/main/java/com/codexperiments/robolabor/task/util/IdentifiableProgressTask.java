package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentifiable;
import com.codexperiments.robolabor.task.TaskProgress;

public interface IdentifiableProgressTask<TResult> extends Task<TResult>, TaskIdentifiable, TaskProgress
{
}
