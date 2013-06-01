package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskProgress;

public interface ProgressTask<TResult> extends Task<TResult>, TaskProgress
{
}
