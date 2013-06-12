package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskProgress;

public interface ProgressTask<TResult> extends Task<TResult>, TaskProgress
{
}
