package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

public interface ProgressTaskResult<TResult> extends TaskResult<TResult>, TaskProgress
{
}
