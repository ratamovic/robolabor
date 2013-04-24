package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentity;

public interface UniqueTask<TResult> extends Task<TResult>, TaskIdentity {
}