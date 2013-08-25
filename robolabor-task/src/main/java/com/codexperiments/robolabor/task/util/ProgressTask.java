package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskProgress;

public interface ProgressTask<TParam, TProgress, TResult> extends Task<TParam, TProgress, TResult>, TaskProgress<TProgress> {
}
