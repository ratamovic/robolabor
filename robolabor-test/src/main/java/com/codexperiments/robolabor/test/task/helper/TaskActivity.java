package com.codexperiments.robolabor.test.task.helper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.TestApplicationContext;

public class TaskActivity extends FragmentActivity
{
    @Deprecated
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

    public BackgroundTask runInnerTask(Integer pTaskResult)
    {
        return runInnerTask(pTaskResult, false);
    }

    public BackgroundTask runInnerTaskStepByStep(Integer pTaskResult)
    {
        return runInnerTask(pTaskResult, true);
    }

    private BackgroundTask runInnerTask(final Integer pTaskResult, boolean pStepByStep)
    {
        final Boolean lCheckEmitterNull = Boolean.valueOf(getIntent().getBooleanExtra("CheckEmitterNull", false));
        final BackgroundTask lBackgroundTask = new InnerBackgroundTask(pTaskResult, lCheckEmitterNull, pStepByStep);
        runOnUiThread(new Runnable() {
            public void run()
            {
                mTaskManager.execute(lBackgroundTask);
            }
        });
        return lBackgroundTask;
    }

    public BackgroundTask runInnerTaskWithId(final Integer pTaskId, final Integer pTaskResult)
    {
        final Boolean lCheckEmitterNull = Boolean.valueOf(getIntent().getBooleanExtra("CheckEmitterNull", false));
        final BackgroundTask lBackgroundTask = new InnerBackgroundTaskWithId(pTaskId, pTaskResult, lCheckEmitterNull, false);
        runOnUiThread(new Runnable() {
            public void run()
            {
                mTaskManager.execute(lBackgroundTask);
            }
        });
        return lBackgroundTask;
    }

    public BackgroundTask runStaticTask(final Integer pTaskResult)
    {
        final BackgroundTask lBackgroundTask = new StaticBackgroundTask(pTaskResult, null);
        runOnUiThread(new Runnable() {
            public void run()
            {
                mTaskManager.execute(lBackgroundTask);
            }
        });
        return lBackgroundTask;
    }

    public BackgroundTask runStandardTask(final Integer pTaskResult)
    {
        final BackgroundTask lBackgroundTask = new BackgroundTask(pTaskResult, null, false);
        runOnUiThread(new Runnable() {
            public void run()
            {
                mTaskManager.execute(lBackgroundTask);
            }
        });
        return lBackgroundTask;
    }

    public void rerunTask(final BackgroundTask pBackgroundTask)
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                mTaskManager.execute(pBackgroundTask);
            }
        });
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
        public InnerBackgroundTask(Integer pTaskResult, Boolean pCheckOwnerIsNull, boolean pStepByStep)
        {
            super(pTaskResult, pCheckOwnerIsNull, pStepByStep);
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


    private class InnerBackgroundTaskWithId extends InnerBackgroundTask implements TaskIdentity
    {
        private Integer mTaskId;

        public InnerBackgroundTaskWithId(Integer pTaskId, Integer pTaskResult, Boolean pCheckOwnerIsNull, boolean pStepByStep)
        {
            super(pTaskResult, pCheckOwnerIsNull, pStepByStep);
            mTaskId = pTaskId;
        }

        @Override
        public Object getId()
        {
            return mTaskId;
        }
    }


    private static class StaticBackgroundTask extends BackgroundTask
    {
        public StaticBackgroundTask(Integer pTaskResult, Boolean pCheckOwnerIsNull)
        {
            super(pTaskResult, pCheckOwnerIsNull, false);
        }
    }
}
