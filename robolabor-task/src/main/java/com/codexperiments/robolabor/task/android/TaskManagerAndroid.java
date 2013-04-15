package com.codexperiments.robolabor.task.android;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.exception.InternalException;
import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskRule;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

public class TaskManagerAndroid implements TaskManager
{
    /*private*/ List<TaskRule<?>> mRules;
    /*private*/ Map<Object, WeakReference<?>> mTaskOwnersByType;
    /*private*/ List<TaskContainerAndroid<?>> mTaskContainers;

    /*private*/ Handler mUIQueue;
    /*private*/ Thread mUIThread;
    /*private*/ ExecutorService mMainExecutor;


    public TaskManagerAndroid() {
        super();
        mTaskContainers = new LinkedList<TaskContainerAndroid<?>>();
        mRules = new ArrayList<TaskRule<?>>();
        mTaskOwnersByType = new HashMap<Object, WeakReference<?>>();
        
        mUIQueue = new Handler(Looper.getMainLooper());
        mUIThread = mUIQueue.getLooper().getThread();
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
        mTaskOwnersByType.put(computeId(pOwner), new WeakReference<Object>(pOwner));
        for (TaskContainerAndroid<?> lTaskContainer : mTaskContainers) {
            lTaskContainer.finish(false);
        }
    }

    @Override
    public void unmanage(Object pOwner) {
        WeakReference<?> lWeakRef = mTaskOwnersByType.get(computeId(pOwner));
        if ((lWeakRef != null) && (lWeakRef.get() == pOwner)) {
            lWeakRef.clear();
        }
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

    protected Object unmap(TaskContainerAndroid<?> pContainer) {
        Object lOuter = (Object) saveOuterRef(pContainer.mTask);
        if (lOuter == null) return null;
        
        Object lOuterId = computeId(lOuter);
        mTaskOwnersByType.put(lOuterId, new WeakReference<Object>(lOuter));
        mMainExecutor.execute(pContainer);
        return lOuterId;
    }

    protected boolean map(TaskContainerAndroid<?> pContainer) {
        WeakReference<?> lOuterRef = mTaskOwnersByType.get(pContainer.mOwnerId);
        boolean lIsSuccess = restoreOuterRef(pContainer.mTask, lOuterRef.get());
        mTaskContainers.remove(this);
        return lIsSuccess;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object computeId(Object pOwner) {
        Class<?> lTargetType = pOwner.getClass();
        for (TaskRule lRule : mRules) {
            if (lTargetType == lRule.getTargetType()) {
                return lRule.getTargetId(pOwner);
            }
        }
        throw InternalException.invalidConfiguration(null); // TODO!!
    }

    public Object saveOuterRef(Object pHandler) {
        // Dereference the outer class to avoid any conflict.
        try {
            Field lOuterRefField = findOuterRefField(pHandler);
         // TODO if null if isinnerclass
            Object lOuterRef = lOuterRefField.get(pHandler);
            lOuterRefField.set(pHandler, null);
            return lOuterRef;
        } catch (IllegalArgumentException eIllegalArgumentException) {
            throw InternalException.illegalCase();
        } catch (IllegalAccessException eIllegalAccessException) {
            throw InternalException.illegalCase();
        }
    }

    public boolean restoreOuterRef(Object pObject, Object pOuterRef) {
        // Dereference the outer class to avoid any conflict.
        try {
            if (pOuterRef == null) return false;
            
            Field lOuterRefField = findOuterRefField(pObject);
            lOuterRefField.setAccessible(true);
            lOuterRefField.set(pObject, pOuterRef);
            return true;
        } catch (IllegalArgumentException eIllegalArgumentException) {
            throw InternalException.illegalCase();
        } catch (IllegalAccessException eIllegalAccessException) {
            throw InternalException.illegalCase();
        }
    }

    private Field findOuterRefField(Object pHandler) {
        Field[] lFields = pHandler.getClass().getDeclaredFields();
        for (Field lField : lFields) {
            String lFieldName = lField.getName();
            if (lFieldName.startsWith("this$")) {
                lField.setAccessible(true);
                return lField;
            }
        }
        return null;
    }


    private class TaskContainerAndroid<TResult> implements TaskBuilder<TResult>, Runnable {
        /*private*/ Task<TResult> mTask;
        /*private*/ Object mTaskId;
        /*private*/ Object mOwnerId;
        
        /*private*/ boolean mKeepResultOnHold;
        
        /*private*/ TResult mResult;
        /*private*/ Throwable mThrowable;
        /*private*/ boolean mProcessed;
        /*private*/ boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask) {
            super();
            mTask = pTask;
            mKeepResultOnHold = true;
            mProcessed = false;
            mFinished = false;
        }

        public TaskContainerAndroid<TResult> singleInstance(Object pTaskId) {
            mTaskId = pTaskId;
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

        @Override
        public void inMainQueue() {
            mOwnerId = unmap(this);
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
            } finally {
                finish(true);
            }
        }

        // On Executor-thread or UI-thread
        protected void finish(final boolean pConfirmProcessed) {
            if (Thread.currentThread() != mUIThread) {
                mUIQueue.post(new Runnable() {
                    public void run() {
                        finish(pConfirmProcessed);
                    }
                });
            } else {
                if (pConfirmProcessed) mProcessed = true;
                if (mFinished || !mProcessed) return;
                
                if (map(this) || !mKeepResultOnHold) {
                    try {
                        if (mThrowable == null) {
                            mTask.onFinish(mResult);
                        } else {
                            mTask.onError(mThrowable);
                        }
                    } finally {
                        mFinished = true;
                    }
                }
            }
        }

        @Override
        public boolean equals(Object pOther) {
            if (mTaskId != null) {
                return mTaskId.equals(pOther);
            } else {
                return super.equals(pOther);
            }
        }

        @Override
        public int hashCode() {
            if (mTaskId != null) {
                return mTaskId.hashCode();
            } else {
                return super.hashCode();
            }
        }
    }
}
