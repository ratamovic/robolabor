package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;

public interface ProgressTaskResult<TResult> extends TaskResult<TResult>, TaskProgress
{
}
