package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;

public interface IdentifiableTask<TParam, TProgress, TResult> extends Task<TParam, TProgress, TResult>, TaskIdentifiable {
}
