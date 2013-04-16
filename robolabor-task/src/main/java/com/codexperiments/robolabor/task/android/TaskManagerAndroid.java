package com.codexperiments.robolabor.task.android;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.exception.InternalException;
import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

public class TaskManagerAndroid implements TaskManager
{
    private TaskManager.Configuration mTaskResolver;
    private Map<Object, WeakReference<?>> mTaskOwnersByType;
    private List<TaskContainerAndroid<?>> mTaskContainers;

    private Handler mUIQueue;
    private Thread mUIThread;


    public TaskManagerAndroid(TaskManager.Configuration pTaskResolver) {
        super();
        mTaskResolver = pTaskResolver;
        mTaskContainers = new LinkedList<TaskContainerAndroid<?>>();
        mTaskOwnersByType = new HashMap<Object, WeakReference<?>>();
        
        mUIQueue = new Handler(Looper.getMainLooper());
        mUIThread = mUIQueue.getLooper().getThread();
    }

    public void start() {
    }

    public void stop() {
    }
    
    @Override
    public void manage(Object pOwner) {
        mTaskOwnersByType.put(mTaskResolver.resolveOwnerId(pOwner), new WeakReference<Object>(pOwner));
        for (TaskContainerAndroid<?> lTaskContainer : mTaskContainers) {
            finish(lTaskContainer, false);
        }
    }

    @Override
    public void unmanage(Object pOwner) {
        WeakReference<?> lWeakRef = mTaskOwnersByType.get(mTaskResolver.resolveOwnerId(pOwner));
        if ((lWeakRef != null) && (lWeakRef.get() == pOwner)) {
            lWeakRef.clear();
        }
    }

    @Override
    public <TResult> void execute(Task<TResult> pTask) {
        Task.Configuration lConfiguration = mTaskResolver.resolveConfiguration(pTask);
        Object lOwnerId = dereferenceOwner(pTask);
        TaskContainerAndroid<TResult> lContainer = makeContainer(lOwnerId, pTask, lConfiguration);
        lConfiguration.getExecutor().execute(lContainer);
    }

    protected Object dereferenceOwner(Task<?> pTask) {
        // Dereference the outer class to avoid any conflict.
        try {
            Field lOwnerField = resolveOwnerField(pTask);
            // TODO if null if isinnerclass
            Object lOwner = lOwnerField.get(pTask);
            lOwnerField.set(pTask, null);

            if (lOwner != null) {
                Object lOwnerId = mTaskResolver.resolveOwnerId(lOwner);
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
    
    protected <TResult> TaskContainerAndroid<TResult> makeContainer(Object pOwnerId,
                                                                    Task<TResult> pTask,
                                                                    Task.Configuration pConfiguration)
    {
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
            
            if (pContainer.mProcessed && !pContainer.mFinished && referenceOwner(pContainer)) {
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

    protected <TResult> boolean referenceOwner(TaskContainerAndroid<TResult> pContainer) {
        WeakReference<?> lOwnerRef = mTaskOwnersByType.get(pContainer.mOwnerId);
        try {
            if (lOwnerRef != null || !pContainer.mConfiguration.keepResultOnHold()) {
                Field lOwnerField = resolveOwnerField(pContainer.mTask);
                // TODO Check null
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


    private class TaskContainerAndroid<TResult> implements Runnable {
        Task<TResult> mTask;
        Object mOwnerId;

        Object mTaskId;
        Task.Configuration mConfiguration;
        
        TResult mResult;
        Throwable mThrowable;
        boolean mProcessed;
        boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask, Task.Configuration pConfiguration, Object pOwnerId, Object pTaskId) {
            super();
            mTask = pTask;
            mOwnerId = null;
            
            mTaskId = pTaskId;
            mConfiguration = pConfiguration;

            mProcessed = false;
            mFinished = false;
        }

        public TaskContainerAndroid(Task<TResult> pTask, Task.Configuration pConfiguration, Object pOwnerId) {
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
