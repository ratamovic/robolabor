package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.task.android.configuration.DefaultConfiguration;
import com.codexperiments.robolabor.test.common.TestApplication;
import com.codexperiments.robolabor.test.common.TestCase;

public class TaskManagerFragmentTest extends TestCase<TaskFragment.Activity> {
    private static final int TIMEOUT = 60000;

    public TaskManagerFragmentTest() {
        super(TaskFragment.Activity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void setUpOnUIThread() throws Exception {
        super.setUpOnUIThread();
        TaskManagerAndroid lTaskManager = new TaskManagerAndroid(new DefaultConfiguration());
        mApplicationContext.registerManager(lTaskManager);
    }

    @Override
    protected void tearDown() throws Exception {
        mApplicationContext.removeManager(TaskManager.class);
        super.tearDown();
    }

    public void testExecute_noFragmentRecreation_withId() throws InterruptedException {
        final Integer TASK_RESULT = 111;

        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        CountDownLatch lTaskFinished = lInitialFragment.runTask(TASK_RESULT);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));

        TaskFragment.Activity lFinalActivity = (TaskFragment.Activity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithId();
        assertThat(lFinalFragment, equalTo(lInitialFragment)); // Ensures fragment is still the same.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withTag() throws InterruptedException {
        final Integer TASK_RESULT = 111;

        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        CountDownLatch lTaskFinished = lInitialFragment.runTask(TASK_RESULT);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));

        TaskFragment.Activity lFinalActivity = (TaskFragment.Activity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithTag();
        assertThat(lFinalFragment, equalTo(lInitialFragment)); // Ensures fragment is still the same.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withId() throws InterruptedException {
        final Integer TASK_RESULT = 222;

        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        CountDownLatch taskFinished = lInitialFragment.runTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));

        TaskFragment.Activity lFinalActivity = (TaskFragment.Activity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithId();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensures fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withTag() throws InterruptedException {
        final Integer TASK_RESULT = 222;

        TaskFragment lInitialFragment = getActivity().getFragmentWithTag();
        CountDownLatch taskFinished = lInitialFragment.runTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(taskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));

        TaskFragment.Activity lFinalActivity = (TaskFragment.Activity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithTag();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensures fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withId() throws InterruptedException {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskFragment.Activity lInitialActivity = getActivity();
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        CountDownLatch lTaskFinished = lInitialFragment.runTask(333);

        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
    }

    public void testExecute_fragmentDestroyed_withTag() throws InterruptedException {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskFragment.Activity lInitialActivity = getActivity();
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag();
        CountDownLatch lTaskFinished = lInitialFragment.runTask(333);

        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));
    }
}
