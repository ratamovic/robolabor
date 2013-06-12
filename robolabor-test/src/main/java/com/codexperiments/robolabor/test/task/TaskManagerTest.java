package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;

import android.test.UiThreadTest;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskManagerConfig;
import com.codexperiments.robolabor.task.TaskManagerException;
import com.codexperiments.robolabor.task.TaskRef;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.task.android.TaskManagerConfigAndroid;
import com.codexperiments.robolabor.task.android.TaskManagerServiceAndroid;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;
import com.codexperiments.robolabor.test.common.TestCase;
import com.codexperiments.robolabor.test.task.helper.BackgroundTask;
import com.codexperiments.robolabor.test.task.helper.BackgroundTaskResult;
import com.codexperiments.robolabor.test.task.helper.TaskActivity;
import com.codexperiments.robolabor.test.task.helper.TaskActivity.HierarchicalTask;
import com.codexperiments.robolabor.test.task.helper.TaskActivity.HierarchicalTask_CorruptionBug;
import com.codexperiments.robolabor.test.task.helper.TaskEmitter;
import com.codexperiments.robolabor.test.task.helper.TaskFragment;

/**
 * TODO Failure cases.
 * 
 * TODO keepResultOnHold cases.
 */
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
        ++mTaskResult;
    }

    private Integer nextId()
    {
        return ++mTaskId;
    }

    private Integer nextResult()
    {
        return ++mTaskResult;
    }

    @Override
    protected void setUpOnUIThread() throws Exception
    {
        super.setUpOnUIThread();
        TaskManagerConfig lConfig = new TaskManagerConfigAndroid(getApplication());
        mTaskManager = new TaskManagerAndroid(getApplication(), lConfig);
        getApplicationContext().registerManager(mTaskManager);
    }

    public void testExecute_inner_unmanaged_persisting() throws InterruptedException
    {
        TaskEmitter lInitialEmitter = TaskEmitter.persisting(getApplicationContext());
        BackgroundTask lTask = lInitialEmitter.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialEmitter.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialEmitter.getTaskException(), nullValue());
    }

    public void testExecute_inner_unmanaged_destroyed() throws InterruptedException
    {
        TaskEmitter lInitialEmitter = TaskEmitter.stepByStepDestroyed(getApplicationContext());
        BackgroundTask lTask = lInitialEmitter.runInnerTask(mTaskResult);
        assertThat(lTask.awaitStepExecuted(), equalTo(true));

        // Try to ensure the emitter gets garbage collected. WARNING: We don't have full control on the garbage collector so we
        // can guarantee this will work! This test may fail at any moment although it works for now. Such a failure occur may
        // occur in the BackgroundTask when checking if emitter is null (it should be null but it won't be in case of failure). A
        // failure could also mean there is a memory-leak somewhere...
        lInitialEmitter = null;
        garbageCollect();

        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_inner_managed_persisting_activity() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_managed_recreated_activity() throws InterruptedException
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

    public void testExecute_inner_managed_destroyed_activity() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_inner_managed_persisting_fragmentWithId() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithId();
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialFragment.getTaskException(), nullValue());
    }

    public void testExecute_inner_managed_recreated_fragmentWithId() throws InterruptedException
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

    public void testExecute_inner_managed_destroyed_fragmentWithId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.getFragmentWithId().runInnerTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_inner_managed_persisting_fragmentWithTag() throws InterruptedException
    {
        TaskFragment lInitialFragment = getActivity().getFragmentWithTag(); // Look here.
        BackgroundTask lTask = lInitialFragment.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialFragment.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialFragment.getTaskException(), nullValue());
    }

    public void testExecute_inner_managed_recreated_fragmentWithTag() throws InterruptedException
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

    public void testExecute_inner_managed_destroyed_fragmentWithTag() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.getFragmentWithTag().runInnerTask(mTaskResult); // Look here.
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_inner_hierarchical_persisting() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        HierarchicalTask lTask = lInitialActivity.runHierarchicalTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lTask.getInnerTask().awaitFinished(), equalTo(true));

        assertThat((lInitialActivity.getTaskResult() & 0x000000ff) >> 0, equalTo(mTaskResult));
        assertThat((lInitialActivity.getTaskResult() & 0x0000ff00) >> 8, equalTo(mTaskResult + 1));
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_hierarchical_recreated() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        HierarchicalTask lTask = lInitialActivity.runHierarchicalTask(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));
        rotateActivitySeveralTimes(4);
        assertThat(lTask.getInnerTask().awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat(lInitialActivity.getTaskResult(), nullValue()); // TODO Do the same for other recreation tests.
        assertThat((lFinalActivity.getTaskResult() & 0x000000ff) >> 0, equalTo(mTaskResult));
        assertThat((lFinalActivity.getTaskResult() & 0x0000ff00) >> 8, equalTo(mTaskResult + 1));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }

    public void testExecute_inner_hierarchical_destroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        HierarchicalTask lTask = lInitialActivity.runHierarchicalTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lTask.getInnerTask().awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getInnerTask().getTaskResult(), equalTo(mTaskResult + 1));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_managed_persisting() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), nullValue());
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_static_managed_recreated() throws InterruptedException
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

    public void testExecute_static_managed_destroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runStaticTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_managed_persisting() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), nullValue());
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_standard_managed_recreated() throws InterruptedException
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

    public void testExecute_standard_managed_destroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runStandardTask(mTaskResult);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTask.getTaskException(), nullValue());
    }

    public void testExecute_severalTasks_serial_withDifferentIds() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTaskWithId(nextId(), mTaskResult);
        // Execute a new task. Since tasks are executed serially, this one will overwrite previous one result.
        BackgroundTask lNewTask = lInitialActivity.runInnerTaskWithId(nextId(), nextResult()); // Expect a new result.
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lNewTask.awaitFinished(), equalTo(true)); // Ensure second task is executed too.

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the second task.
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_severalTasks_serial_withSameId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTaskWithId(nextId(), mTaskResult);
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

    public void testExecute_severalTasks_serial_withAndWithoutId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTaskWithId(nextId(), mTaskResult);
        // Execute a new task. Since tasks are executed serially, this one will overwrite previous one result.
        BackgroundTask lNewTask = lInitialActivity.runInnerTask(nextResult()); // Expect a new result.
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lNewTask.awaitFinished(), equalTo(true)); // Ensure second task is executed too.

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the second task.
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_severalTasks_serial_withoutAndWithId() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        // Execute a new task. Since tasks are executed serially, this one will overwrite previous one result.
        BackgroundTask lNewTask = lInitialActivity.runInnerTaskWithId(nextId(), nextResult()); // Expect a new result.
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lNewTask.awaitFinished(), equalTo(true)); // Ensure second task is executed too.

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult)); // Result should be the one of the second task.
        assertThat(lInitialActivity.getTaskException(), nullValue());
    }

    public void testExecute_reuseTask() throws Throwable
    {
        TaskActivity lInitialActivity = getActivity();
        final BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialActivity.getTaskException(), nullValue());

        // Execute previous task again, which is not allowed by TaskManager, hence the exception that is raised.
        lTask.reset();
        final AtomicBoolean lFailure = new AtomicBoolean(false);
        runTestOnUiThread(new Runnable() {
            public void run()
            {
                try {
                    mTaskManager.execute(lTask);
                    fail();
                } catch (TaskManagerException eTaskManagerException) {
                    lFailure.set(true);
                }
            }
        });
        assertThat(lTask.awaitFinished(), equalTo(false));
        assertThat(lFailure.get(), equalTo(true));
    }

    public void testExecute_progress_persisting() throws InterruptedException
    {
        assertThat(isServiceRunning(TaskManagerServiceAndroid.class), equalTo(false)); // TODO Check service somewhere else.
        TaskActivity lInitialActivity = getActivity(TaskActivity.stepByStep());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);

        // Progress counter is incremented for each step.
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(1));

        assertThat(isServiceRunning(TaskManagerServiceAndroid.class), equalTo(true));
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(2));

        assertThat(isServiceRunning(TaskManagerServiceAndroid.class), equalTo(true));
        assertThat(lTask.awaitStepExecuted(), equalTo(true));
        assertThat(lTask.awaitProgressExecuted(), equalTo(true));
        assertThat(lTask.getProgressCounter(), equalTo(3));

        // Finish the task. Since all progress notifications have been processed, no more notifications happen.
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(isServiceRunning(TaskManagerServiceAndroid.class), equalTo(false));
        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTask.getProgressCounter(), equalTo(3));
    }

    public void testExecute_progress_recreated() throws InterruptedException
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

    public void testExecute_progress_destroyed() throws InterruptedException
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

    public void testExecute_persisting_failure() throws InterruptedException
    {
        Exception lTaskException = new Exception("Something happened");
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(lTaskException);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), nullValue());
        assertThat(lInitialActivity.getTaskException(), sameInstance((Throwable) lTaskException));
    }

    public void testExecute_recreated_failure() throws InterruptedException
    {
        Exception lTaskException = new Exception("Something happened");
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(lTaskException);
        rotateActivitySeveralTimes(1);
        assertThat(lTask.awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lFinalActivity.getTaskResult(), nullValue());
        assertThat(lFinalActivity.getTaskException(), sameInstance((Throwable) lTaskException));
    }

    public void testExecute_recreated_destroyed() throws InterruptedException
    {
        Exception lTaskException = new Exception("Something happened");
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runInnerTask(lTaskException);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTask.awaitFinished(), equalTo(true));

        assertThat(lTask.getTaskResult(), nullValue());
        assertThat(lTask.getTaskException(), sameInstance((Throwable) lTaskException));
    }

    public void testRebind_inner_managed_persisting() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        BackgroundTaskResult lTaskResult = lInitialActivity.rebindInnerTask(lTask, true);
        assertThat(lTaskResult.awaitFinished(), equalTo(true));

        assertThat(lInitialActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lInitialActivity.getTaskException(), nullValue());
        assertThat(lTaskResult.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTaskResult.getTaskException(), nullValue());
    }

    public void testRebind_inner_managed_recreated() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        rotateActivitySeveralTimes(2);
        BackgroundTaskResult lTaskResult = lInitialActivity.rebindInnerTask(lTask, true);
        rotateActivitySeveralTimes(2);
        assertThat(lTaskResult.awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat(lFinalActivity, not(equalTo(lInitialActivity))); // Ensure activity has been recreated.
        assertThat(lFinalActivity.getTaskResult(), equalTo(mTaskResult));
        assertThat(lFinalActivity.getTaskException(), nullValue());
        assertThat(lTaskResult.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTaskResult.getTaskException(), nullValue());
    }

    public void testRebind_inner_managed_destroyed() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity(TaskActivity.dying());
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        // TODO There is a race condion here
        BackgroundTaskResult lTaskResult = lInitialActivity.rebindInnerTask(lTask, true);
        lInitialActivity = terminateActivity(lInitialActivity);
        assertThat(lTaskResult.awaitFinished(), equalTo(true));

        assertThat(lTaskResult.getTaskResult(), equalTo(mTaskResult));
        assertThat(lTaskResult.getTaskException(), nullValue());
    }

    public void testRebind_inner_managed_afterTaskEnded() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        BackgroundTask lTask = lInitialActivity.runInnerTask(mTaskResult);
        assertThat(lTask.awaitFinished(), equalTo(true));

        BackgroundTaskResult lTaskResult = lInitialActivity.rebindInnerTask(lTask, false);
        assertThat(lTaskResult.awaitFinished(), equalTo(false));
        assertThat(lTaskResult.getTaskResult(), nullValue());
        assertThat(lTaskResult.getTaskException(), nullValue());
    }

    @UiThreadTest
    public void testRebind_inner_managed_nonExistingTask() throws Throwable
    {
        boolean lBound = mTaskManager.rebind(new TaskRef<Integer>(Integer.MAX_VALUE), new TaskResult<Integer>() {
            public void onFinish(TaskManager pTaskManager, Integer pTaskResult)
            {
            }

            public void onFail(TaskManager pTaskManager, Throwable pTaskException)
            {
            }
        });
        assertThat(lBound, equalTo(false));
    }

    @UiThreadTest
    public void testExecute_failure_taskNull() throws InterruptedException
    {
        try {
            mTaskManager.manage(null);
            fail();
        } catch (NullPointerException eNullPointerException) {
        }

        try {
            mTaskManager.unmanage(null);
            fail();
        } catch (NullPointerException eNullPointerException) {
        }

        try {
            mTaskManager.execute(null);
            fail();
        } catch (NullPointerException eNullPointerException) {
        }

        try {
            mTaskManager.rebind(null, new BackgroundTaskResult());
            fail();
        } catch (NullPointerException eNullPointerException) {
        }

        try {
            mTaskManager.rebind(new TaskRef<Integer>(0), null);
            fail();
        } catch (NullPointerException eNullPointerException) {
        }
    }

    public void testExecute_failure_notCalledFromUIThread() throws InterruptedException
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
            mTaskManager.rebind(new TaskRef<Integer>(0), new BackgroundTaskResult());
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

    public void testExecute_failure_notCalledFromATask() throws InterruptedException
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

    /**
     * Bug that causes hierarchical tasks to corrupt emitter list. Shouldn't occur anymore.
     */
    public void testExecute_inner_hierarchical_corruptionBug() throws InterruptedException
    {
        TaskActivity lInitialActivity = getActivity();
        HierarchicalTask_CorruptionBug lTask = lInitialActivity.runHierarchicalTask_corruptionBug(mTaskResult);
        rotateActivitySeveralTimes(4);
        assertThat(lTask.awaitFinished(), equalTo(true));
        assertThat(lTask.getBackgroundTask2().awaitFinished(), equalTo(true));

        TaskActivity lFinalActivity = getActivity();
        assertThat((lFinalActivity.getTaskResult() & 0x000000ff) >> 0, equalTo(mTaskResult));
        assertThat((lFinalActivity.getTaskResult() & 0x0000ff00) >> 8, equalTo(mTaskResult + 1));
        assertThat(lFinalActivity.getTaskException(), nullValue());
    }
}
