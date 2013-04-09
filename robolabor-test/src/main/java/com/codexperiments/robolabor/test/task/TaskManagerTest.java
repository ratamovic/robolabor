package com.codexperiments.robolabor.test.task;

import static java.lang.Thread.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.test.common.TestApplication;
import com.codexperiments.robolabor.test.common.TestCase;

public class TaskManagerTest extends TestCase<TaskActivity> {
    private static final int TIMEOUT = 60000;
    
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
//        TaskManagerAndroid lTaskManagerAndroid = new TaskManagerAndroid();
//        mApplicationContext.registerManager(lTaskManagerAndroid);
        TaskManagerAndroid lTaskManager = new TaskManagerAndroid();
        mApplicationContext.registerManager(lTaskManager);
    }

    @Override
    protected void tearDown() throws Exception {
        mApplicationContext.removeManager(TaskManager.class);
        super.tearDown();
    }

    public void testExecute_noActivityRecreation() throws InterruptedException {
        final Integer TASK_RESULT = 666;
        
        TaskActivity lActivity = getActivity();
        CountDownLatch taskFinished = lActivity.runTask(TASK_RESULT);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
        
        TaskActivity lCurrentActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lCurrentActivity, equalTo(lActivity)); // Ensures activity is still the same.
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
        assertThat(lCurrentActivity, not(equalTo(lActivity))); // Ensures activity has been recreated.
        assertThat(lCurrentActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lCurrentActivity.getTaskException(), nullValue());
    }

    public void testExecute_activityDestroyed() throws InterruptedException {
        setActivityIntent(TaskActivity.activityToDestroy());
        TaskActivity lActivity = getActivity();
        CountDownLatch taskFinished = lActivity.runTask(999);
        
        lActivity = terminateActivity(lActivity);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
    }

    public void testExecute_xxx() throws InterruptedException {
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
}
