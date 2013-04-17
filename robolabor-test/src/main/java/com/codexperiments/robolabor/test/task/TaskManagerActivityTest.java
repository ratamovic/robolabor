package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.test.common.TestApplication;
import com.codexperiments.robolabor.test.common.TestCase;

public class TaskManagerActivityTest extends TestCase<TaskActivity> {
    private static final int TIMEOUT = 60000;
    
    public TaskManagerActivityTest() {
        super(TaskActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void setUpOnUIThread() throws Exception {
        super.setUpOnUIThread();
        TaskManagerAndroid lTaskManager = new TaskManagerAndroid(new TaskManagerAndroid.DefaultConfiguration());
        mApplicationContext.registerManager(lTaskManager);
    }

    @Override
    protected void tearDown() throws Exception {
        mApplicationContext.removeManager(TaskManager.class);
        super.tearDown();
    }

    public void testExecute_noActivityRecreation() throws InterruptedException {
        final Integer TASK_RESULT = 111;
        
        TaskActivity lInitialActivity = getActivity();
        CountDownLatch lTaskFinished = lInitialActivity.runTask(TASK_RESULT);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
        
        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, equalTo(lInitialActivity)); // Ensures activity is still the same.
        assertThat(lFinalActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_activityRecreation() throws InterruptedException {
        final Integer TASK_RESULT = 222;
        
        TaskActivity lInitialActivity = getActivity();
        CountDownLatch lTaskFinished = lInitialActivity.runTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
        
        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensures activity has been recreated.
        assertThat(lFinalActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_activityDestroyed() throws InterruptedException {
        setActivityIntent(TaskActivity.activityToDestroy());
        TaskActivity lInitialActivity = getActivity();
        CountDownLatch lTaskFinished = lInitialActivity.runTask(999);
        
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
    }
}
