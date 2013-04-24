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

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.util.ProgressiveTask;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.TestApplication;
import com.codexperiments.robolabor.test.common.TestApplicationContext;

public class TaskFragment extends Fragment {
    private static final int TASK_DURATION = 5000;
    
    private View mView;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;
    
    public static TaskFragment newInstanceCheckNull() {
        TaskFragment fragment = new TaskFragment();

        Bundle args = new Bundle();
        args.putBoolean("CheckFragmentNull", true);
        fragment.setArguments(args);

        return fragment;
    }

    public static TaskFragment newInstance() {
        TaskFragment fragment = new TaskFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    @Override
    public void onCreate(Bundle pBundle) {
        super.onCreate(pBundle);
        
        TestApplication.Instance.setCurrentFragment(this);
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
        final CountDownLatch lTaskFinished = new CountDownLatch(1);
        final boolean lCheckFragmentNull = getArguments().getBoolean("CheckFragmentNull", false);
        
        mTaskManager.execute(new ProgressiveTask<Integer>() {
            public Integer onProcess(TaskManager pTaskManager) throws Exception {
                pTaskManager.notifyProgress(this);
                Thread.sleep(TASK_DURATION);
                return pTaskResult;
            }

            public void onProgress(TaskManager pTaskManager) {
            }

            public void onFinish(TaskManager pTaskManager, Integer pTaskResult) {
                if (lCheckFragmentNull) {
                    assertThat(TaskFragment.this, nullValue());
                } else if (TaskFragment.this != null) {
                    mTaskResult = pTaskResult;
                }
                lTaskFinished.countDown();
            }

            public void onError(TaskManager pTaskManager, Throwable pThrowable) {
                mTaskException = pThrowable;
            }
        });
        return lTaskFinished;
    }

    public Integer getTaskResult() {
        return mTaskResult;
    }

    public Throwable getTaskException() {
        return mTaskException;
    }



    public static class Activity extends FragmentActivity {
        private TaskManager mTaskManager;
        
        @Override
        protected void onCreate(Bundle pBundle) {
            super.onCreate(pBundle);
            setContentView(R.layout.main);
            
            TestApplication.Instance.setCurrentActivity(this);
            TestApplicationContext lApplicationContext = TestApplicationContext.from(this);
            mTaskManager = lApplicationContext.getManager(TaskManager.class);
            mTaskManager.manage(this);
            

            if (pBundle == null) {
                getSupportFragmentManager().beginTransaction()
                .add(0, TaskFragment.newInstance(), "uniquetag")
                .replace(R.id.activity_content, TaskFragment.newInstance())
                .add(0, TaskFragment.newInstance())
                .commit();
            }
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
        
        public TaskFragment getFragmentWithId() {
            return (TaskFragment) getSupportFragmentManager().findFragmentById(R.id.activity_content);
        }
        
        public TaskFragment getFragmentWithTag() {
            return (TaskFragment) getSupportFragmentManager().findFragmentByTag("uniquetag");
        }
        
        public TaskFragment getFragmentNoIdNorTag() {
            return (TaskFragment) getSupportFragmentManager().findFragmentById(0);
        }
    }
}
