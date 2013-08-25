package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskNotifier;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;
import com.codexperiments.robolabor.task.handler.TaskStart;
import com.codexperiments.robolabor.task.id.TaskId;
import com.codexperiments.robolabor.task.id.UniqueTaskId;

public class TaskAdapter<TParam, TProgress, TResult>
    implements Task<TParam, TProgress, TResult>, TaskResult<TResult>, TaskStart, TaskIdentifiable, TaskProgress<TProgress>
{
    private TaskId mId;

    public TaskAdapter() {
        super();
        mId = new UniqueTaskId();
    }

    @Override
    public TaskId getId() {
        return mId;
    }

    @Override
    public void onStart(boolean pIsRestored) {
    }

    @Override
    public TResult onProcess(TParam pParam, TaskNotifier<TProgress> pNotifier) throws Exception {
        return null;
    }

    @Override
    public void onProgress(TProgress pProgress) {
    }

    @Override
    public void onFinish(TResult pResult) {
    }

    @Override
    public void onFail(Throwable pException) {
    }
}
