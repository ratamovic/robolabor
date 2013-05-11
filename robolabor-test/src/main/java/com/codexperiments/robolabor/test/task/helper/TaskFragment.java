package com.codexperiments.robolabor.test.task.helper;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.TestApplicationContext;

public class TaskFragment extends Fragment
{
    private View mView;

    private boolean mCheckEmitterNull;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;

    public static TaskFragment newInstance(boolean pCheckEmitterNull)
    {
        Bundle lBundle = new Bundle();
        lBundle.putBoolean("CheckEmitterNull", pCheckEmitterNull);

        TaskFragment fragment = new TaskFragment();
        fragment.setArguments(lBundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle pBundle)
    {
        super.onCreate(pBundle);

        mCheckEmitterNull = getArguments().getBoolean("CheckEmitterNull", false);

        TestApplicationContext lApplicationContext = TestApplicationContext.from(this);
        mTaskManager = lApplicationContext.getManager(TaskManager.class);
        mTaskManager.manage(this);

        mTaskResult = null;
        mTaskException = null;

        if (pBundle != null) {
            mTaskResult = (Integer) pBundle.getSerializable("TaskResult");
        }
    }

    @Override
    public View onCreateView(LayoutInflater pInflater, ViewGroup pContainer, Bundle pBundle)
    {
        super.onCreateView(pInflater, pContainer, pBundle);
        mView = pInflater.inflate(R.layout.main, pContainer, false);
        return mView;
    }

    @Override
    public void onStart()
    {
        super.onStart();
        mTaskManager.manage(this);
    }

    @Override
    public void onStop()
    {
        super.onStop();
        mTaskManager.unmanage(this);
    }

    @Override
    public void onSaveInstanceState(Bundle pBundle)
    {
        super.onSaveInstanceState(pBundle);
        pBundle.putSerializable("TaskResult", mTaskResult);
    }

    public BackgroundTask runInnerTask(final Integer pTaskResult)
    {
        final BackgroundTask lInnerBackgroundTask = new InnerBackgroundTask(pTaskResult, mCheckEmitterNull);
        getActivity().runOnUiThread(new Runnable() {
            public void run()
            {
                mTaskManager.execute(lInnerBackgroundTask);
            }
        });
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

    private class InnerBackgroundTask extends BackgroundTask
    {
        public InnerBackgroundTask(Integer pTaskResult, Boolean pCheckOwnerIsNull)
        {
            super(pTaskResult, pCheckOwnerIsNull);
        }

        @Override
        public Object getEmitter()
        {
            return TaskFragment.this;
        }

        @Override
        public void setResult(Integer pTaskResult, Throwable pTaskException)
        {
            TaskFragment.this.mTaskResult = pTaskResult;
            TaskFragment.this.mTaskException = pTaskException;
        }
    }
}
