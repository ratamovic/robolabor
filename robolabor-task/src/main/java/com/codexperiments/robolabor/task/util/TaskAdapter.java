package com.codexperiments.robolabor.task.util;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;
import com.codexperiments.robolabor.task.handler.TaskStart;
import com.codexperiments.robolabor.task.id.TaskId;
import com.codexperiments.robolabor.task.id.UniqueTaskId;

public class TaskAdapter<TResult> implements Task<TResult>, TaskResult<TResult>, TaskStart, TaskIdentifiable, TaskProgress
{
    private TaskId mId;

    public TaskAdapter()
    {
        super();
        mId = new UniqueTaskId();
    }

    @Override
    public TaskId getId()
    {
        return mId;
    }

    @Override
    public void onStart(boolean pIsRestored)
    {
    }

    @Override
    public TResult onProcess(TaskManager pTaskManager) throws Exception
    {
        return null;
    }

    @Override
    public void onProgress(TaskManager pTaskManager)
    {
    }

    @Override
    public void onFinish(TaskManager pTaskManager, TResult pResult)
    {
    }

    @Override
    public void onFail(TaskManager pTaskManager, Throwable pException)
    {
    }
}
