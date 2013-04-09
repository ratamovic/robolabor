package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.ApplicationContext;
import com.codexperiments.robolabor.test.common.TestApplication;

public class TaskFragment extends Fragment {
    private static final int TASK_DURATION = 5000;
    
    private View mView;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;
    
    public static TaskFragment fragmentToDestroy() {
        TaskFragment fragment = new TaskFragment();

        Bundle args = new Bundle();
        args.putBoolean("CheckActivityNull", true);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle pBundle) {
        super.onCreate(pBundle);
        
        TestApplication.Instance.setCurrentFragment(this);
        ApplicationContext lApplicationContext = ApplicationContext.from(this);
        mTaskManager = lApplicationContext.getManager(TaskManager.class);
        mTaskManager.manage(this);

        mTaskResult = null;
        mTaskException = null;

        if (pBundle != null) {
            mTaskResult = (Integer) pBundle.getSerializable("TaskResult");
        }
    }

    @Override
    public View onCreateView(LayoutInflater pInflater, ViewGroup pContainer, Bundle pBundle) {
        super.onCreateView(pInflater, pContainer, pBundle);
        mView = pInflater.inflate(R.layout.main, pContainer, false);
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mTaskManager.manage(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mTaskManager.unmanage(this);
    }
    
    @Override
    public void onSaveInstanceState(Bundle pBundle) {
        super.onSaveInstanceState(pBundle);
        pBundle.putSerializable("TaskResult", mTaskResult);
    }

    public CountDownLatch runTask(final Integer pTaskResult) {
        final CountDownLatch taskFinished = new CountDownLatch(1);
        final boolean lCheckActivityNull = getArguments().getBoolean("CheckActivityNull", false);
        
        mTaskManager.execute(new Task<Integer>() {
            public Integer onProcess() throws Exception {
                Thread.sleep(TASK_DURATION);
                return pTaskResult;
            }

            public void onFinish(Integer pTaskResult) {
                if (lCheckActivityNull) {
                    assertThat(TaskFragment.this, nullValue());
                } else if (TaskFragment.this != null) {
                    mTaskResult = pTaskResult;
                }
                taskFinished.countDown();
            }

            public void onError(Throwable pThrowable) {
                mTaskException = pThrowable;
            }
        }).dontKeepResult().inMainQueue();
        return taskFinished;
    }

    public Integer getTaskResult() {
        return mTaskResult;
    }

    public Throwable getTaskException() {
        return mTaskException;
    }



    public static class TaskActivity extends FragmentActivity {
        private TaskManager mTaskManager;
        
        @Override
        protected void onCreate(Bundle pBundle) {
            super.onCreate(pBundle);
            setContentView(R.layout.main);
            
            TestApplication.Instance.setCurrentActivity(this);
            ApplicationContext lApplicationContext = ApplicationContext.from(this);
            mTaskManager = lApplicationContext.getManager(TaskManager.class);
            mTaskManager.manage(this);
        }

        @Override
        protected void onStart() {
            super.onStart();
            mTaskManager.manage(this);
        }

        @Override
        protected void onStop() {
            super.onStop();
            mTaskManager.unmanage(this);
        }
    }
}
