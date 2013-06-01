package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentifiable;

public interface IdentifiableTask<TResult> extends Task<TResult>, TaskIdentifiable
{
}
