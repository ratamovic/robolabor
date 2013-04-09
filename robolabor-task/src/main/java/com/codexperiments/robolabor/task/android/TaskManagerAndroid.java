package com.codexperiments.robolabor.task.android;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskContext;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

public class TaskManagerAndroid implements TaskManager
{
    /*private*/ List<TaskContainerAndroid<?>> mTaskContainers;
    /*private*/ TaskContextActivity mContextActivity;

    /*private*/ Handler mUIQueue;
    /*private*/ ExecutorService mMainExecutor;


    public TaskManagerAndroid() {
        super();
        mTaskContainers = new LinkedList<TaskContainerAndroid<?>>();
        mContextActivity = new TaskContextActivity();
        
        mUIQueue = new Handler(Looper.getMainLooper());
        mMainExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable pRunnable) {
                Thread thread = new Thread(pRunnable);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void start() {
    }

    public void stop() {
        mMainExecutor.shutdown();
    }
    
    @Override
    public void manage(Object pOwner) {
        mContextActivity.manage(pOwner);
        
        for (TaskContainerAndroid<?> lTaskContainer : mTaskContainers) {
            if (lTaskContainer.isProcessed() && !lTaskContainer.isFinished()) {
                lTaskContainer.finish();
            }
        }
    }

    @Override
    public void unmanage(Object pOwner) {
        mContextActivity.unmanage(pOwner);
    }

    @Override
    public <TResult> TaskBuilder<TResult> execute(Task<TResult> pTask) {
        return new TaskContainerAndroid<TResult>(pTask);
    }

    @Override
    public <TResult> boolean listenPending(TaskResult<TResult> pTaskListener) {
        return true;
    }

    @Override
    public void notifyProgress(final TaskProgress pProgress) {
        mUIQueue.post(new Runnable() {
            @Override
            public void run() {
                pProgress.onProgress();
            }
        });
    }



    private class TaskContainerAndroid<TResult> implements TaskBuilder<TResult>, Runnable {
        /*private*/ Task<TResult> mTask;
        /*private*/ TaskContext mContext;
        /*private*/ Object mId;
        
        /*private*/ boolean mKeepResultOnHold;
        
        /*private*/ TResult mResult;
        /*private*/ Throwable mThrowable;
        /*private*/ boolean mProcessed;
        /*private*/ boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask) {
            super();
            mTask = pTask;
            mKeepResultOnHold = true;
            mFinished = false;
        }

        public TaskContainerAndroid<TResult> singleInstance(Object pId) {
            mId = pId;
            return this;
        }

        @Override
        public TaskContainerAndroid<TResult> dontKeepResult() {
            mKeepResultOnHold = false;
            return this;
        }

        @Override
        public TaskContainerAndroid<TResult> keepResultOnHold() {
            mKeepResultOnHold = true;
            return this;
        }

        private TaskContext findContext() {
            return mContextActivity;
        }

        @Override
        public void inMainQueue() {
            mContext = findContext();
            mContext.unmap(mTask);
            mMainExecutor.execute(this);
        }

        @Override
        public void inBackgroundQueue() {
        }

        // On Executor-thread
        public void run() {
            try {
                mResult = mTask.onProcess();
            } catch (final Exception eException) {
                mThrowable = eException;
            }
            finish();
        }

        // On Executor-thread or UI-thread
        private void finish() {
            mUIQueue.post(new Runnable() {
                public void run() {
                    mProcessed = true;
                    if (mFinished) return;
                    
                    if (mContext.map(mTask) || !mKeepResultOnHold) {
                        if (mThrowable == null) {
                            mTask.onFinish(mResult);
                        } else {
                            mTask.onError(mThrowable);
                        }
                        mFinished = true;
                        mTaskContainers.remove(this);
                    }
                }
            });
        }
        
        public boolean isProcessed() {
            return mProcessed;
        }
        
        public boolean isFinished() {
            return mFinished;
        }

        @Override
        public boolean equals(Object pOther) {
            if (mId != null) {
                return mId.equals(pOther);
            } else {
                return super.equals(pOther);
            }
        }

        @Override
        public int hashCode() {
            if (mId != null) {
                return mId.hashCode();
            } else {
                return super.hashCode();
            }
        }
    }
}
