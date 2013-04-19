package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.ApplicationContext;
import com.codexperiments.robolabor.test.common.TestApplication;

public class TaskActivity extends Activity {
    private static final int TASK_DURATION = 5000;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;
    
    public static Intent activityToDestroy() {
        Intent lIntent = new Intent();
        lIntent.putExtra("CheckActivityNull", true);
        return lIntent;
    }

    @Override
    protected void onCreate(Bundle pBundle) {
        super.onCreate(pBundle);
        setContentView(R.layout.main);
        
        TestApplication.Instance.setCurrentActivity(this);
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
    protected void onStart() {
        super.onStart();
        mTaskManager.manage(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTaskManager.unmanage(this);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle pBundle) {
        super.onSaveInstanceState(pBundle);
        pBundle.putSerializable("TaskResult", mTaskResult);
    }

    public CountDownLatch runTask(final Integer pTaskResult) {
        final CountDownLatch lTaskFinished = new CountDownLatch(1);
        final boolean lCheckActivityNull = getIntent().getBooleanExtra("CheckActivityNull", false);
        
        mTaskManager.execute(new TaskProgress<Integer>() {
            public Integer onProcess(TaskManager pTaskManager) throws Exception {
                pTaskManager.notifyProgress();
                Thread.sleep(TASK_DURATION);
                return pTaskResult;
            }

            public void onProgress(TaskManager pTaskManager) {
            }

            public void onFinish(TaskManager pTaskManager, Integer pTaskResult) {
                if (lCheckActivityNull) {
                    assertThat(TaskActivity.this, nullValue());
                } else if (TaskActivity.this != null) {
                    mTaskResult = pTaskResult;
                }
                lTaskFinished.countDown();
            }

            public void onError(TaskManager pTaskManager, Throwable pThrowable) {
                mTaskException = pThrowable;
            }
        }); // .dontKeepResult().inMainQueue()
        return lTaskFinished;
    }

    public Integer getTaskResult() {
        return mTaskResult;
    }

    public Throwable getTaskException() {
        return mTaskException;
    }
}
