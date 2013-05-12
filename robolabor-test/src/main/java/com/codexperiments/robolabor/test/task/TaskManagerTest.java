package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import android.test.UiThreadTest;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.task.android.TaskManagerException;
import com.codexperiments.robolabor.task.android.configuration.DefaultConfiguration;
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
        getApplicationContext().registerManager(mTaskManager);
    }

    public void testExecute_inner_noActivityRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_activityRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_activityDestroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialFragment.getTaskException(), nullValue());
    }

    public void testExecute_noFragmentRecreation_withTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));

        TaskFragment lFinalFragment = getActivity().getFragmentWithId();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensure fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentRecreation_withTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));

        TaskFragment lFinalFragment = getActivity().getFragmentWithTag();
        assertThat(lFinalFragment, not(equalTo(lInitialFragment))); // Ensure fragment has been recreated.
        assertThat(lFinalFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalFragment.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.getFragmentWithId().runInnerTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_fragmentDestroyed_withTag() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.getFragmentWithTag().runInnerTask(mTaskResult); // Look here.
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), nullValue());
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure emitter has been recreated.
        assertThat(lFinalActivity.getTaskResult(), nullValue());
        assertThat(lFinalActivity.getTaskException(), nullValue());
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_emitterDestroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), nullValue());
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure emitter has been recreated.
        assertThat(lFinalActivity.getTaskResult(), nullValue());
        assertThat(lFinalActivity.getTaskException(), nullValue());
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_emitterDestroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_severalTasksWithNoId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        // Execute a new task. Since tasks are executed serially, this one will overwrite previous one result.
        BackgroundTask lNewTask = lInitialActivity.runInnerTask(nextResult()); // Expect a new result.
        // Execute previous task again. Since previous task has not been fully executed, this one will not be enqueued.
        // If it was, an exception would be raised because the CountDownLatch must be equal to 1 in BackgroundTask.
        // In addition, the task has been dereferenced at this point. So TaskManager would raise an exception anyway.
        lInitialActivity.rerunTask(lNewTask);
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lNewTask.awaitFinished(), equalTo(true)); // Ensure second task is executed too.

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the first task.
        assertThat(lInitialActivity.getTaskException(), nullValue());

        // Execute previous task again. Since previous execution is fully finished, this one will be enqueued.
        lNewTask.reset(nextResult());
        lInitialActivity.rerunTask(lNewTask); // Expect a new result.
        assertThat(lNewTask.awaitFinished(), equalTo(true));
        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the last execution.
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_severalTasksWithSameId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTaskWithId(mTaskId, mTaskResult);
        // Execute a new task with the same Id. Since previous task has not been fully executed, this one will not be enqueued.
        BackgroundTask lNewTask = lInitialActivity.runInnerTaskWithId(mTaskId, mTaskResult + 1); // Keep old mTaskResult value.
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lNewTask.awaitFinished(), equalTo(false)); // Ensure second task is not executed as one is in the queue.

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the first task.
        assertThat(lInitialActivity.getTaskException(), nullValue());

        // Execute a new task with the same Id. Since previous task has been fully executed, this one will be enqueued.
        BackgroundTask l2ndNewTask = lInitialActivity.runInnerTaskWithId(mTaskId, nextResult()); // Expect a new result.
        assertThat(l2ndNewTask.awaitFinished(), equalTo(true));
        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the last task.
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_notifyProgress_noEmitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.stepByStep());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);

        // Progress counter is incremented for each step.
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(2));

        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(3));

        // Finish the task. Since all progress notifications have been processed, no more notifications happen.
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTask.getProgressCounter(), equalTo(3));
    }

    public void testExecute_notifyProgress_emitterRecreation() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.stepByStep());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        // Terminate the emitter. No more progress notification starting from here.
        terminateActivity(lInitialActivity);
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(false));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(false));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        // Emitter is recreated so progress notification should be back on track.
        TaskActivity lFinalActivity = getActivity();
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(2));

        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(3));

        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalActivity.getTaskException(), nullValue());
        assertThat(lTask.getProgressCounter(), equalTo(3));
    }

    public void testExecute_notifyProgress_emitterDestroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.stepByStepDying());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        // No more progress starting from here.
        terminateActivity(lInitialActivity);
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(false));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(false));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
        assertThat(lTask.getProgressCounter(), equalTo(1));
    }

    public void testExecute_notifyProgressNotFromATask() throws InterruptedException
    {
        try {
            mTaskManager.notifyProgress(new TaskProgress() {
                public void onProgress(TaskManager pTaskManager)
                {
                }
            });
            fail();
        } catch (TaskManagerException eTaskManagerException) {
            // Success
        }
    }

    public void testExecute_notCalledFromUIThread() throws InterruptedException
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

        try {
            mTaskManager.listen(new TaskResult<Integer>() {
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

        try {
            mTaskManager.manage(new Object());
            fail();
        } catch (TaskManagerException eTaskManagerException) {
            // Success
        }

        try {
            mTaskManager.unmanage(new Object());
            fail();
        } catch (TaskManagerException eTaskManagerException) {
            // Success
        }
    }

    @UiThreadTest
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
