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
import com.codexperiments.robolabor.task.TaskConfiguration;
import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskRuleExecutor;
import com.codexperiments.robolabor.task.TaskRuleMapping;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

public class TaskManagerAndroid implements TaskManager
{
    /*private*/ List<TaskRuleMapping<?>> mRuleMappings;
    /*private*/ List<TaskRuleExecutor<?>> mRuleExecutors;
    /*private*/ Map<Object, WeakReference<?>> mTaskOwnersByType;
    /*private*/ List<TaskContainerAndroid<?>> mTaskContainers;

    /*private*/ Handler mUIQueue;
    /*private*/ Thread mUIThread;
    /*private*/ ExecutorService mMainExecutor;


    public TaskManagerAndroid() {
        super();
        mRuleMappings = new ArrayList<TaskRuleMapping<?>>();
        mRuleExecutors = new ArrayList<TaskRuleExecutor<?>>();
        mTaskContainers = new LinkedList<TaskContainerAndroid<?>>();
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
        mTaskOwnersByType.put(resolveOwnerId(pOwner), new WeakReference<Object>(pOwner));
        for (TaskContainerAndroid<?> lTaskContainer : mTaskContainers) {
            finish(lTaskContainer, false);
        }
    }

    @Override
    public void unmanage(Object pOwner) {
        WeakReference<?> lWeakRef = mTaskOwnersByType.get(resolveOwnerId(pOwner));
        if ((lWeakRef != null) && (lWeakRef.get() == pOwner)) {
            lWeakRef.clear();
        }
    }

    @Override
    public <TResult> void execute(Task<TResult> pTask) {
        TaskConfiguration lConfiguration = resolveConfiguration(pTask);
        Object lOwnerId = dereference(pTask);
        TaskContainerAndroid<TResult> lContainer = buildContainer(pTask, lConfiguration, lOwnerId);
        
        lConfiguration.getExecutor().execute(lContainer);
    }

//    @Override
//    public <TResult> void execute(Task<TResult> pTask, TaskConfiguration pConfiguration) {
//        Object lOwnerId = dereference(pTask);
//        TaskContainerAndroid<TResult> lContainer = buildContainer(pTask, pConfiguration, lOwnerId);
//        
//        pConfiguration.getExecutor().execute(lContainer);
//    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private TaskConfiguration resolveConfiguration(Task<?> pTask) {
        Class<?> lTaskType = pTask.getClass();
        for (TaskRuleExecutor lRuleExecutor : mRuleExecutors) {
            if (lTaskType == lRuleExecutor.getTaskType()) {
                return lRuleExecutor.getConfiguration(pTask);
            }
        }
        throw InternalException.invalidConfiguration(null); // TODO!!
    }

    protected Object dereference(Task<?> pTask) {
        // Dereference the outer class to avoid any conflict.
        try {
            Field lOwnerField = resolveOwnerField(pTask);
            // TODO if null if isinnerclass
            Object lOwner = lOwnerField.get(pTask);
            lOwnerField.set(pTask, null);

            if (lOwner != null) {
                Object lOwnerId = resolveOwnerId(lOwner);
                mTaskOwnersByType.put(lOwnerId, new WeakReference<Object>(lOwner));
                return lOwnerId;
            } else {
                return null;
            }
        } catch (IllegalArgumentException eIllegalArgumentException) {
            throw InternalException.illegalCase();
        } catch (IllegalAccessException eIllegalAccessException) {
            throw InternalException.illegalCase();
        }
    }
    
    protected <TResult> TaskContainerAndroid<TResult> buildContainer(Task<TResult> pTask,
                                                                     TaskConfiguration pConfiguration,
                                                                     Object pOwnerId) {
        if (pTask instanceof TaskIdentity) {
            Object lTaskId = ((TaskIdentity) pTask).getId();
            return new TaskContainerAndroid<TResult>(pTask, pConfiguration, pOwnerId, lTaskId);
        } else {
            return new TaskContainerAndroid<TResult>(pTask, pConfiguration, pOwnerId);
        }
    }

    @Override
    public <TResult> boolean listen(TaskResult<TResult> pTaskListener) {
        return true;
    }

    @Override
    public void notifyProgress(final TaskProgress pProgress) {
        mUIQueue.post(new Runnable() {
            public void run() {
                pProgress.onProgress();
            }
        });
    }

    // On Executor-thread or UI-thread
    protected <TResult> void finish(final TaskContainerAndroid<TResult> pContainer, final boolean pConfirmProcessed) {
        if (Thread.currentThread() != mUIThread) {
            mUIQueue.post(new Runnable() {
                public void run() {
                    finish(pContainer, pConfirmProcessed);
                }
            });
        } else {
            if (pConfirmProcessed) pContainer.mProcessed = true;
            
            if (pContainer.mProcessed && !pContainer.mFinished && canFinish(pContainer)) {
                try {
                    if (pContainer.mThrowable == null) {
                        pContainer.mTask.onFinish(pContainer.mResult);
                    } else {
                        pContainer.mTask.onError(pContainer.mThrowable);
                    }
                } finally {
                    pContainer.mFinished = true;
                }
            }
        }
    }

    protected <TResult> boolean canFinish(TaskContainerAndroid<TResult> pContainer) {
        WeakReference<?> lOwnerRef = mTaskOwnersByType.get(pContainer.mOwnerId);
        try {
            if (lOwnerRef != null || !pContainer.mConfiguration.keepResultOnHold()) {
                Field lOwnerField = resolveOwnerField(pContainer.mTask);
                lOwnerField.setAccessible(true);
                lOwnerField.set(pContainer.mTask, lOwnerRef.get());

                mTaskContainers.remove(this);
                return true;
            } else {
                return false;
            }
        } catch (IllegalArgumentException eIllegalArgumentException) {
            throw InternalException.illegalCase();
        } catch (IllegalAccessException eIllegalAccessException) {
            throw InternalException.illegalCase();
        }
    }

    private Field resolveOwnerField(Object pHandler) {
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object resolveOwnerId(Object pOwner) {
        Class<?> lOwnerType = pOwner.getClass();
        for (TaskRuleMapping lRuleMapping : mRuleMappings) {
            if (lOwnerType == lRuleMapping.getTargetType()) {
                return lRuleMapping.getTargetId(pOwner);
            }
        }
        throw InternalException.invalidConfiguration(null); // TODO!!
    }


    private class TaskContainerAndroid<TResult> implements Runnable {
        Task<TResult> mTask;
        Object mOwnerId;

        Object mTaskId;
        TaskConfiguration mConfiguration;
        
        TResult mResult;
        Throwable mThrowable;
        boolean mProcessed;
        boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask, TaskConfiguration pConfiguration, Object pOwnerId, Object pTaskId) {
            super();
            mTask = pTask;
            mOwnerId = null;
            
            mTaskId = pTaskId;
            mConfiguration = pConfiguration;

            mProcessed = false;
            mFinished = false;
        }

        public TaskContainerAndroid(Task<TResult> pTask, TaskConfiguration pConfiguration, Object pOwnerId) {
            super();
            mTask = pTask;
            mOwnerId = null;
            
            mTaskId = null;
            mConfiguration = pConfiguration;

            mProcessed = false;
            mFinished = false;
        }

        // On Executor-thread
        public void run() {
            try {
                mResult = mTask.onProcess();
            } catch (final Exception eException) {
                mThrowable = eException;
            } finally {
                finish(this, true);
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
