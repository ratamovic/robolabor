package com.codexperiments.robolabor.test.task.helper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.TestApplicationContext;

public class TaskActivity extends FragmentActivity
{
    private boolean mCheckEmitterNull;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;

    public static Intent destroyableActivity()
    {
        Intent lIntent = new Intent();
        lIntent.putExtra("CheckEmitterNull", true);
        return lIntent;
    }

    @Override
    protected void onCreate(Bundle pBundle)
    {
        super.onCreate(pBundle);
        setContentView(R.layout.main);

        mCheckEmitterNull = getIntent().getBooleanExtra("CheckEmitterNull", false);

        TestApplicationContext lApplicationContext = TestApplicationContext.from(this);
        mTaskManager = lApplicationContext.getManager(TaskManager.class);
        mTaskManager.manage(this);

        if (pBundle == null) {
            mTaskResult = null;
            mTaskException = null;

            getSupportFragmentManager().beginTransaction() //
                            .add(0, TaskFragment.newInstance(mCheckEmitterNull), "uniquetag") //
                            .replace(R.id.activity_content, TaskFragment.newInstance(mCheckEmitterNull)) //
                            .add(0, TaskFragment.newInstance(false)) //
                            .commit();
        } else {
            mTaskResult = (Integer) pBundle.getSerializable("TaskResult");
            mTaskException = (Throwable) pBundle.getSerializable("TaskException");
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        mTaskManager.manage(this);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        mTaskManager.unmanage(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle pBundle)
    {
        super.onSaveInstanceState(pBundle);
        pBundle.putSerializable("TaskResult", mTaskResult);
    }

    public BackgroundTask runInnerTask(final Integer pTaskResult)
    {
        final Boolean lCheckEmitterNull = Boolean.valueOf(getIntent().getBooleanExtra("CheckEmitterNull", false));
        BackgroundTask lInnerBackgroundTask = new InnerBackgroundTask(pTaskResult, lCheckEmitterNull);

        mTaskManager.execute(lInnerBackgroundTask);
        return lInnerBackgroundTask;
    }

    public BackgroundTask runStaticTask(final Integer pTaskResult)
    {
        BackgroundTask lInnerBackgroundTask = new StaticBackgroundTask(pTaskResult, null);

        mTaskManager.execute(lInnerBackgroundTask);
        return lInnerBackgroundTask;
    }

    public BackgroundTask runStandardTask(final Integer pTaskResult)
    {
        BackgroundTask lInnerBackgroundTask = new BackgroundTask(pTaskResult, null);

        mTaskManager.execute(lInnerBackgroundTask);
        return lInnerBackgroundTask;
    }

    public Integer getTaskResult()
    {
        return mTaskResult;
    }

    public Throwable getTaskException()
    {
        return mTaskException;
    }

    public TaskFragment getFragmentWithId()
    {
        return (TaskFragment) getSupportFragmentManager().findFragmentById(R.id.activity_content);
    }

    public TaskFragment getFragmentWithTag()
    {
        return (TaskFragment) getSupportFragmentManager().findFragmentByTag("uniquetag");
    }

    public TaskFragment getFragmentNoIdNorTag()
    {
        return (TaskFragment) getSupportFragmentManager().findFragmentById(0);
    }

    private class InnerBackgroundTask extends BackgroundTask
    {
        public InnerBackgroundTask(Integer pTaskResult, Boolean pCheckOwnerIsNull)
        {
            super(pTaskResult, pCheckOwnerIsNull);
        }

        @Override
        public Object getEmitter()
        {
            return TaskActivity.this;
        }

        @Override
        public void setResult(Integer pTaskResult, Throwable pTaskException)
        {
            TaskActivity.this.mTaskResult = pTaskResult;
            TaskActivity.this.mTaskException = pTaskException;
        }
    }

    private static class StaticBackgroundTask extends BackgroundTask
    {
        public StaticBackgroundTask(Integer pTaskResult, Boolean pCheckOwnerIsNull)
        {
            super(pTaskResult, pCheckOwnerIsNull);
        }
    }
}
