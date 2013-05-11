package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import com.codexperiments.robolabor.task.Task;
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
    private Integer mTaskId;
    private Integer mTaskResult;
    private TaskManagerAndroid mTaskManager;

    public TaskManagerTest()
    {
        super(TaskActivity.class);
        mTaskId = 0;
        mTaskResult = 0;
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        ++mTaskId;
        ++mTaskResult;
    }

    private Integer nextResult()
    {
        return ++mTaskResult;
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
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, equalTo(lInitialActivity)); // Ensure activity is still the same.
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_activityRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_activityDestroyed() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithId();
        assertThat(lFinalFragment, equalTo(lInitialFragment)); // Ensure fragment is still the same.
        assertThat(lFinalFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithTag();
        assertThat(lFinalFragment, equalTo(lInitialFragment)); // Ensure fragment is still the same.
        assertThat(lFinalFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithId();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensure fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, notNullValue());
        TaskFragment lFinalFragment = lFinalActivity.getFragmentWithTag();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensure fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withId() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.getFragmentWithId().runInnerTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withTag() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.getFragmentWithTag().runInnerTask(mTaskResult); // Look here.
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_emitterDestroyed() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        recreateActivitySeveralTimes(4);
        assertThat(lTask.await(), equalTo(true));

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_emitterDestroyed() throws InterruptedException
    {
        setActivityIntent(TaskActivity.destroyableActivity());
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.await(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_severalTasksWithNoId() throws InterruptedException
    {
        TaskActivity lActivity = getActivity();
        BackgroundTask lTask = lActivity.runInnerTask(mTaskResult);
        // Execute a new task. Since tasks are executed serially, this one will overwrite previous one result.
        BackgroundTask lNewTask = lActivity.runInnerTask(nextResult()); // Expect a new result.
        // Execute previous task again. Since previous task has not been fully executed, this one will not be enqueued.
        // If it was, an exception would be raised because the CountDownLatch must be equal to 1 in BackgroundTask.
        // In addition, the task has been dereferenced at this point. So TaskManager would raise an exception anyway.
        lActivity.rerunTask(lNewTask);
        assertThat(lTask.await(), equalTo(true));
        assertThat(lNewTask.await(), equalTo(true)); // Ensure second task is executed too.

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the first task.
        assertThat(lFinalActivity.getTaskException(), nullValue());

        // Execute previous task again. Since previous execution is fully finished, this one will be enqueued.
        lNewTask.reset(nextResult());
        lActivity.rerunTask(lNewTask); // Expect a new result.
        assertThat(lNewTask.await(), equalTo(true));
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the last execution.
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_severalTasksWithSameId() throws InterruptedException
    {
        TaskActivity lActivity = getActivity();
        BackgroundTask lTask = lActivity.runInnerTaskWithId(mTaskId, mTaskResult);
        // Execute a new task with the same Id. Since previous task has not been fully executed, this one will not be enqueued.
        BackgroundTask lNewTask = lActivity.runInnerTaskWithId(mTaskId, mTaskResult + 1); // Keep old mTaskResult value.
        assertThat(lTask.await(), equalTo(true));
        assertThat(lNewTask.await(), equalTo(false)); // Ensure second task is not executed as one is in the queue.

        TaskActivity lFinalActivity = (TaskActivity) TestApplication.Instance.getCurrentActivity();
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the first task.
        assertThat(lFinalActivity.getTaskException(), nullValue());

        // Execute a new task with the same Id. Since previous task has been fully executed, this one will be enqueued.
        BackgroundTask l2ndNewTask = lActivity.runInnerTaskWithId(mTaskId, nextResult()); // Expect a new result.
        assertThat(l2ndNewTask.await(), equalTo(true));
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the last task.
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_taskNotExecutedFromUIThread() throws InterruptedException
    {
        try {
            mTaskManager.execute(new Task<Integer>() {
                public Integer onProcess(TaskManager pTaskManager) throws Exception
                {
                    return null;
                }

                public void onFinish(TaskManager pTaskManager, Integer pTaskResult)
                {
                }

                public void onFail(TaskManager pTaskManager, Throwable pTaskException)
                {
                }
            });
            fail();
        } catch (TaskManagerException eTaskManagerException) {
            // Success
        }
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
