package com.codexperiments.robolabor.task;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.codexperiments.robolabor.task.context.ActivityTaskContext;

import android.os.Handler;
import android.os.Looper;

public class AndroidTaskManager implements TaskManager
{
    /*private*/ List<AndroidTaskContainer<?>> mTaskContainers;
    /*private*/ ActivityTaskContext mContextActivity;

    /*private*/ Handler mUIQueue;
    /*private*/ ExecutorService mMainExecutor;


    public AndroidTaskManager() {
        super();
        mTaskContainers = new LinkedList<AndroidTaskContainer<?>>();
        mContextActivity = new ActivityTaskContext();
        
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
        
        for (AndroidTaskContainer<?> lTaskContainer : mTaskContainers) {
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
        return new AndroidTaskContainer<TResult>(pTask);
    }

    @Override
    public <TResult> boolean listenPending(TaskCallback<TResult> pTaskListener) {
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



    private class AndroidTaskContainer<TResult> implements TaskBuilder<TResult>, Runnable {
        /*private*/ Task<TResult> mTask;
        /*private*/ TaskContext mContext;
        /*private*/ Object mId;
        
        /*private*/ boolean mKeepResultOnHold;
        
        /*private*/ TResult mResult;
        /*private*/ Throwable mThrowable;
        /*private*/ boolean mProcessed;
        /*private*/ boolean mFinished;

        public AndroidTaskContainer(Task<TResult> pTask) {
            super();
            mTask = pTask;
            mKeepResultOnHold = true;
            mFinished = false;
        }

        public AndroidTaskContainer<TResult> singleInstance(Object pId) {
            mId = pId;
            return this;
        }

        @Override
        public AndroidTaskContainer<TResult> dontKeepResult() {
            mKeepResultOnHold = false;
            return this;
        }

        @Override
        public AndroidTaskContainer<TResult> keepResultOnHold() {
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
