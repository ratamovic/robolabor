package com.codexperiments.robolabor.test.task;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid;
import com.codexperiments.robolabor.task.android.configuration.DefaultConfiguration;
import com.codexperiments.robolabor.test.common.TestCase;

public class TaskManagerStaticTest extends TestCase<TaskActivity>
{
    private static final int TIMEOUT = 10000;

    private TaskManagerAndroid mTaskManager;

    public TaskManagerStaticTest()
    {
        super(TaskActivity.class);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
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

    public void testExecute_static() throws InterruptedException
    {
        final Integer TASK_RESULT = 111;

        TaskStatic lTaskStatic = new TaskStatic(mTaskManager);
        CountDownLatch lTaskFinished = lTaskStatic.runTask(TASK_RESULT);
        assertThat(lTaskFinished.await(TIMEOUT, TimeUnit.MILLISECONDS), equalTo(true));

        assertThat(lTaskStatic.getTaskResult(), equalTo(TASK_RESULT));
        assertThat(lTaskStatic.getTaskException(), nullValue());
    }
}
