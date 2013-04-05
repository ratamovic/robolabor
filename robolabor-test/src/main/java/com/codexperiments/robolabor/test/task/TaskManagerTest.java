package com.codexperiments.robolabor.test.task;

import static java.lang.Thread.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import com.codexperiments.robolabor.task.TaskHandler;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskManagerAndroid;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.ApplicationContext;
import com.codexperiments.robolabor.test.common.TestApplication;
import com.codexperiments.robolabor.test.common.TestCase;
import com.codexperiments.robolabor.test.task.TaskManagerTest.TaskActivity;

public class TaskManagerTest extends TestCase<TaskActivity> {
    private static final int TIMEOUT = 60000;
    private static final int TASK_DURATION = 5000;
    
    public TaskManagerTest() {
        super(TaskActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void setUpOnUIThread() throws Exception {
        super.setUpOnUIThread();
        mApplicationContext.registerManager(new TaskManagerAndroid());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testExecute_noActivityRecreation() throws InterruptedException {
        final Integer TASK_RESULT = 666;
        
        TaskActivity lActivity = getActivity();
        CountDownLatch taskFinished = lActivity.runTask(TASK_RESULT);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
        
        TaskActivity lCurrentActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lCurrentActivity, equalTo(lActivity)); // Checks activity is still the same.
        assertThat(lCurrentActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lCurrentActivity.getTaskException(), nullValue());
    }

    public void testExecute_activityRecreation() throws InterruptedException {
        final Integer TASK_RESULT = 777;
        
        TaskActivity lActivity = getActivity();
        CountDownLatch taskFinished = lActivity.runTask(TASK_RESULT);
        recreateActivitySeveralTimes(lActivity, 4);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
        
        TaskActivity lCurrentActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lCurrentActivity, not(equalTo(lActivity))); // Checks activity has been recreated.
        assertThat(lCurrentActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lCurrentActivity.getTaskException(), nullValue());
    }

    public void testExecute_activityDestroyed() throws InterruptedException {
        setActivityIntent(TaskActivity.checkActivityWillBeNull());
        TaskActivity lActivity = getActivity();
        CountDownLatch taskFinished = lActivity.runTask(999);
        
        lActivity = terminateActivity(lActivity);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
    }

    private void recreateActivitySeveralTimes(TaskActivity pActivity, int pCount) throws InterruptedException {
        for (int i = 0; i < pCount; ++i) {
            // Wait some time before turning.
            sleep(500);

            Resources lResources = getInstrumentation().getTargetContext().getResources();
            Configuration lConfiguration = lResources.getConfiguration();
            if (lConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                pActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                pActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
    }
    
    private TaskActivity terminateActivity(TaskActivity pActivity) throws InterruptedException {
        TaskActivity lActivity = pActivity;
        lActivity.finish();
        setActivity(null);
        TestApplication.Instance.setCurrentActivity(null);
        return null;
    }
    
    
    public static class TaskActivity extends Activity {
        private TaskManager mTaskManager;
        private Integer mTaskResult;
        private Throwable mTaskException;
        
        public static Intent checkActivityWillBeNull() {
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
            ((TaskManagerAndroid) mTaskManager).manage(this);

            mTaskResult = null;
            mTaskException = null;
            
            if (pBundle != null) {
                mTaskResult = (Integer) pBundle.getSerializable("TaskResult");
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
        }

        @Override
        protected void onStart() {
            super.onStart();
            ((TaskManagerAndroid) mTaskManager).manage(this);
        }

        @Override
        protected void onStop() {
            super.onStop();
            ((TaskManagerAndroid) mTaskManager).unmanage(this);
        }
        
        @Override
        protected void onSaveInstanceState(Bundle pBundle) {
            super.onSaveInstanceState(pBundle);
            pBundle.putSerializable("TaskResult", mTaskResult);
        }

        public CountDownLatch runTask(final Integer pTaskResult) {
            final boolean lCheckActivityNull = getIntent().getBooleanExtra("CheckActivityNull", false);
            final CountDownLatch taskFinished = new CountDownLatch(1);
            Log.d(getClass().getSimpleName(), "111 " + taskFinished);
            
            mTaskManager.execute(new TaskHandler<Integer>() {
                public Integer onProcess() throws Exception {
                    Log.d(getClass().getSimpleName(), "AAA ");
                    Thread.sleep(TASK_DURATION);
                    Log.d(getClass().getSimpleName(), "BBB ");
                    return pTaskResult;
                }

                public void onFinish(Integer pTaskResult) {
                    Log.d(getClass().getSimpleName(), "222 " + TaskActivity.this);
                    if (lCheckActivityNull) {
                        assertThat(TaskActivity.this, nullValue());
                    } else if (TaskActivity.this != null) {
                        Log.d(getClass().getSimpleName(), "333 " + pTaskResult);
                        mTaskResult = pTaskResult;
                    }
                    taskFinished.countDown();
                }

                public void onError(Exception pTaskException) {
                    Log.d(getClass().getSimpleName(), "444 ");
                    mTaskException = pTaskException;
                }
            });
            return taskFinished;
        }

        public Integer getTaskResult() {
            return mTaskResult;
        }

        public Throwable getTaskException() {
            return mTaskException;
        }
    }
}
