package com.codexperiments.robolabor.test.task.helper;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.id.IntTaskId;
import com.codexperiments.robolabor.test.R;
import com.codexperiments.robolabor.test.common.TestApplicationContext;

public class TaskActivity extends FragmentActivity {
    private boolean mCheckEmitterNull;
    private boolean mStepByStep;

    private TaskManager mTaskManager;
    private Integer mTaskResult;
    private Throwable mTaskException;

    public static Intent dying() {
        return createIntent(true, false);
    }

    public static Intent stepByStep() {
        return createIntent(false, true);
    }

    public static Intent stepByStepDying() {
        return createIntent(true, true);
    }

    private static Intent createIntent(boolean pCheckEmitterNull, boolean pStepByStep) {
        Intent lIntent = new Intent();
        lIntent.putExtra("CheckEmitterNull", pCheckEmitterNull);
        lIntent.putExtra("StepByStep", pStepByStep);
        return lIntent;
    }

    @Override
    protected void onCreate(Bundle pBundle) {
        super.onCreate(pBundle);
        setContentView(R.layout.main);

        mCheckEmitterNull = getIntent().getBooleanExtra("CheckEmitterNull", false);
        mStepByStep = getIntent().getBooleanExtra("StepByStep", false);

        TestApplicationContext lApplicationContext = TestApplicationContext.from(this);
        mTaskManager = lApplicationContext.getManager(TaskManager.class);
        mTaskManager.manage(this);

        if (pBundle == null) {
            mTaskResult = null;
            mTaskException = null;

            getSupportFragmentManager().beginTransaction() //
                                       .add(0, TaskFragment.newInstance(mCheckEmitterNull), "uniquetag")
                                       //
                                       .replace(R.id.activity_content, TaskFragment.newInstance(mCheckEmitterNull))
                                       //
                                       .add(0, TaskFragment.newInstance(false))
                                       //
                                       .commit();
        } else {
            mTaskResult = (Integer) pBundle.getSerializable("TaskResult");
            mTaskException = (Throwable) pBundle.getSerializable("TaskException");
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

    public BackgroundTask runInnerTask(final Integer pTaskResult) {
        final BackgroundTask lTask = new InnerTask(pTaskResult, mCheckEmitterNull, mStepByStep);
        runOnUiThread(new Runnable() {
            public void run() {
                lTask.setTaskRef(mTaskManager.execute(lTask));
            }
        });
        return lTask;
    }

    public BackgroundTask runInnerTask(final Exception pTaskException) {
        final BackgroundTask lTask = new InnerTask(pTaskException, mCheckEmitterNull, mStepByStep);
        runOnUiThread(new Runnable() {
            public void run() {
                lTask.setTaskRef(mTaskManager.execute(lTask));
            }
        });
        return lTask;
    }

    public BackgroundTaskResult rebindInnerTask(final BackgroundTask pBackgroundTask, final boolean expectTaskBound) {
        final BackgroundTaskResult lResult = new InnerResult(mCheckEmitterNull);
        runOnUiThread(new Runnable() {
            public void run() {
                boolean lBound = mTaskManager.rebind(pBackgroundTask.getTaskRef(), lResult);
                assertThat(lBound, equalTo(expectTaskBound));
            }
        });
        return lResult;
    }

    public HierarchicalTask runHierarchicalTask(final Integer pTaskResult) {
        final HierarchicalTask lTask = new HierarchicalTask(pTaskResult, mCheckEmitterNull, mStepByStep);
        runOnUiThread(new Runnable() {
            public void run() {
                mTaskManager.execute(lTask);
            }
        });
        return lTask;
    }

    public BackgroundTask runInnerTaskWithId(final Integer pTaskId, final Integer pTaskResult) {
        final BackgroundTask lTask = new InnerTaskWithId(pTaskId, pTaskResult, mCheckEmitterNull, mStepByStep);
        runOnUiThread(new Runnable() {
            public void run() {
                mTaskManager.execute(lTask);
            }
        });
        return lTask;
    }

    public BackgroundTask runStaticTask(final Integer pTaskResult) {
        final BackgroundTask lTask = new StaticTask(pTaskResult, null, mStepByStep);
        runOnUiThread(new Runnable() {
            public void run() {
                mTaskManager.execute(lTask);
            }
        });
        return lTask;
    }

    public BackgroundTask runStandardTask(final Integer pTaskResult) {
        final BackgroundTask lTask = new BackgroundTask(pTaskResult, null, false);
        runOnUiThread(new Runnable() {
            public void run() {
                mTaskManager.execute(lTask);
            }
        });
        return lTask;
    }

    public void rerunTask(final BackgroundTask pBackgroundTask) {
        runOnUiThread(new Runnable() {
            public void run() {
                mTaskManager.execute(pBackgroundTask);
            }
        });
    }

    public Integer getTaskResult() {
        return mTaskResult;
    }

    public Throwable getTaskException() {
        return mTaskException;
    }

    public TaskFragment getFragmentWithId() {
        return (TaskFragment) getSupportFragmentManager().findFragmentById(R.id.activity_content);
    }

    public TaskFragment getFragmentWithTag() {
        return (TaskFragment) getSupportFragmentManager().findFragmentByTag("uniquetag");
    }

    public TaskFragment getFragmentNoIdNorTag() {
        return (TaskFragment) getSupportFragmentManager().findFragmentById(0);
    }

    private class InnerTask extends BackgroundTask {
        public InnerTask(Integer pTaskResult, Boolean pCheckEmitterNull, boolean pStepByStep) {
            super(pTaskResult, pCheckEmitterNull, pStepByStep);
        }

        public InnerTask(Exception pTaskException, Boolean pCheckEmitterNull, boolean pStepByStep) {
            super(pTaskException, pCheckEmitterNull, pStepByStep);
        }

        @Override
        public Object getEmitter() {
            return TaskActivity.this;
        }

        @Override
        public void onFinish(Integer pTaskResult) {
            if (getEmitter() != null) {
                mTaskResult = pTaskResult;
            }
            super.onFinish(pTaskResult);
        }

        @Override
        public void onFail(Throwable pTaskException) {
            if (getEmitter() != null) {
                mTaskException = pTaskException;
            }
            super.onFail(pTaskException);
        }
    }

    private class InnerTaskWithId extends InnerTask implements TaskIdentifiable {
        private IntTaskId mTaskId;

        public InnerTaskWithId(int pTaskId, Integer pTaskResult, Boolean pCheckEmitterNull, boolean pStepByStep) {
            super(pTaskResult, pCheckEmitterNull, pStepByStep);
            mTaskId = new IntTaskId(pTaskId);
        }

        @Override
        public IntTaskId getId() {
            return mTaskId;
        }
    }

    public class HierarchicalTask extends InnerTask {
        private BackgroundTask mInnerTask;

        public HierarchicalTask(final Integer pTaskResult, final Boolean pCheckEmitterNull, final boolean pStepByStep) {
            super(pTaskResult, pCheckEmitterNull, pStepByStep);
        }

        @Override
        public void onFinish(Integer pTaskResult) {
            mInnerTask = new InnerTask(pTaskResult + 1, getCheckEmitterNull(), getStepByStep()) {
                @Override
                public void onFinish(Integer pTaskResult) {
                    super.onFinish((TaskActivity.this != null) ? ((pTaskResult << 8) | mTaskResult) : pTaskResult);
                }
            };
            mTaskManager.execute(mInnerTask);
            super.onFinish(pTaskResult);
        }

        public BackgroundTask getInnerTask() {
            return mInnerTask;
        }
    }

    private static class StaticTask extends BackgroundTask {
        public StaticTask(Integer pTaskResult, Boolean pCheckEmitterNull, boolean pStepByStep) {
            super(pTaskResult, pCheckEmitterNull, pStepByStep);
        }
    }

    private class InnerResult extends BackgroundTaskResult {
        public InnerResult(Boolean pCheckEmitterNull) {
            super(pCheckEmitterNull);
        }

        @Override
        public Object getEmitter() {
            return TaskActivity.this;
        }

        @Override
        public void onFinish(Integer pTaskResult) {
            if (getEmitter() != null) {
                mTaskResult = pTaskResult;
            }
            super.onFinish(pTaskResult);
        }

        @Override
        public void onFail(Throwable pTaskException) {
            if (getEmitter() != null) {
                mTaskException = pTaskException;
            }
            super.onFail(pTaskException);
        }
    }

    public HierarchicalTask_CorruptionBug runHierarchicalTask_corruptionBug(final Integer pTaskResult) {
        final HierarchicalTask_CorruptionBug lTask = new HierarchicalTask_CorruptionBug(pTaskResult,
                                                                                        mCheckEmitterNull,
                                                                                        mStepByStep);
        runOnUiThread(new Runnable() {
            public void run() {
                mTaskManager.execute(lTask);
            }
        });
        return lTask;
    }

    public class HierarchicalTask_CorruptionBug extends InnerTask {
        private BackgroundTask mBackgroundTask2;

        public HierarchicalTask_CorruptionBug(final Integer pTaskResult,
                                              final Boolean pCheckEmitterNull,
                                              final boolean pStepByStep)
        {
            super(pTaskResult, pCheckEmitterNull, pStepByStep);
            mBackgroundTask2 = new InnerTask(pTaskResult + 1, pCheckEmitterNull, pStepByStep) {
                @Override
                public void onFinish(Integer pTaskResult) {
                    super.onFinish((pTaskResult << 8) | mTaskResult);
                }
            };
        }

        @Override
        public void onFinish(Integer pTaskResult) {
            mTaskManager.execute(getBackgroundTask2());
            super.onFinish(pTaskResult);
        }

        public BackgroundTask getBackgroundTask2() {
            return mBackgroundTask2;
        }
    }
}
