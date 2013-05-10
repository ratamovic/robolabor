package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.task.android.TaskManagerException;
import com.codexperiments.robolabor.task.android.configuration.DefaultConfiguration;
import com.codexperiments.robolabor.test.common.TestApplication;
import com.codexperiments.robolabor.test.common.TestCase;
import com.codexperiments.robolabor.test.task.helper.BackgroundTask;
import com.codexperiments.robolabor.test.task.helper.TaskActivity;
import com.codexperiments.robolabor.test.task.helper.TaskFragment;

public class TaskManagerTest extends TestCase<TaskActivity>
{
    private Integer TASK_RESULT = 0;
    private TaskManagerAndroid mTaskManager;

    public TaskManagerTest()
    {
        super(TaskActivity.class);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        TASK_RESULT += 1;
    }

    @Override
    protected void setUpOnUIThread() throws Exception
    {
        super.setUpOnUIThread();
        mTaskManager = new TaskManagerAndroid(new DefaultConfiguration());
        mApplicationContext.registerManager(mTaskManager);
    }

    @Override
    protected void tearDown() throws Exception
    {
        mApplicationContext.removeManager(TaskManager.class);
        super.tearDown();
    }

    public void testExecute_inner_noActivityRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(TASK_RESULT);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, equalTo(lInitialActivity)); // Ensure activity is still the same.
        assertThat(lFinalActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_activityRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lFinalActivity.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_activityDestroyed() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(TASK_RESULT);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(TASK_RESULT);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithId();
        assertThat(lFinalFragment, equalTo(lInitialFragment)); // Ensure fragment is still the same.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(TASK_RESULT);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithTag();
        assertThat(lFinalFragment, equalTo(lInitialFragment)); // Ensure fragment is still the same.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithId();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensure fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithTag();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensure fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withId() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.getFragmentWithId().runInnerTask(TASK_RESULT);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withTag() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.getFragmentWithTag().runInnerTask(TASK_RESULT); // Look here.
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(TASK_RESULT);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_emitterDestroyed() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(TASK_RESULT);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(TASK_RESULT);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(TASK_RESULT);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_emitterDestroyed() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(TASK_RESULT);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_taskNull() throws InterruptedException
    {
        try {
            mTaskManager.execute(null);
            fail();
        } catch (TaskManagerException eTaskManagerException) {
            // Success
        }
    }
}
