package com.codexperiments.robolabor.task.android;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

/**
 * <ul>
 * <li>TODO handler = getWindow().getDecorView().getHandler();</li>
 * <li>TODO Handle Unique tasks.</li>
 * <li>TODO Handle timeout.</li>
 * <li>TODO Handle cancellation.</li>
 * <li>TODO Implement listen().</li>
 * <li>TODO Rework synchronization.</li>
 * </ul>
 */
public class TaskManagerAndroid implements TaskManager
{
    private ManagerConfiguration mManagerConfig;
    private List<TaskContainerAndroid<?>> mContainers;
    private Map<Object, WeakReference<?>> mEmittersById;

    private Handler mUIQueue;
    private Thread mUIThread;

    public TaskManagerAndroid(ManagerConfiguration pConfig)
    {
        super();
        mManagerConfig = pConfig;
        mContainers = Collections.synchronizedList(new LinkedList<TaskContainerAndroid<?>>());
        mEmittersById = new ConcurrentHashMap<Object, WeakReference<?>>();

        mUIQueue = new Handler(Looper.getMainLooper());
        mUIThread = mUIQueue.getLooper().getThread();
    }

    public void start()
    {
    }

    public void stop()
    {
    }

    @Override
    public void manage(Object pEmitter)
    {
        manage(pEmitter, null);
    }

    protected Object manage(Object pEmitter, TaskContainerAndroid<?> pParentContainer)
    {
        // Save the new task emitter in the reference list. Replace the existing one if any according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. See DefaultConfiguration for an example.
        // Note that an emitter Id can be null if no emitter dereferencement should be applied.
        Object lEmitterId = mManagerConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
            // collector do its job.
            mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
            for (TaskContainerAndroid<?> lTaskContainer : mContainers) {
                tryFinish(lTaskContainer, false);
            }
        }
        return lEmitterId;
    }

    @Override
    public void unmanage(Object pEmitter)
    {
        // Remove an existing task emitter. If the emitter reference (in Java terms) is different from the object to remove, then
        // don't do anything. This could occur if a new object is managed before an older one with the same Id is unmanaged.
        // Typically, this could occur for example if an Activity X starts and then navigates to an Activity B which is,
        // according to Android lifecycle, started before A is stopped (the two activities are alive at the same time during a
        // short period of time).
        // Note that an emitter Id can be null if a task is not an inner class and thus has no outer class.
        Object lEmitterId = mManagerConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            WeakReference<?> lWeakRef = mEmittersById.get(lEmitterId);
            if ((lWeakRef != null) && (lWeakRef.get() == pEmitter)) {
                mEmittersById.remove(lEmitterId);
            }
        }
    }

    /**
     * Try to find the reference to an existing object managed by the TaskManager. Three cases may occur:
     * <ul>
     * <li>The returned reference is not null and contains a non-null value: an object is currently managed by the TaskManager.</li>
     * <li>The returned reference is not null but contains a null value: means there was an object managed by the TaskManager but
     * its reference has been lost (like an activity that has been destroyed)... Maybe one will get managed in the future.</li>
     * <li>A null reference is returned: means the specified Id is not managed (or has been unmanaged since) by the TaskManager.
     * <li>
     * </ul>
     * 
     * @param pEmitterId Id of the object that must be resolved.
     * @return Weak reference pointing to the managed object. May be null or contain a null reference.
     */
    protected WeakReference<?> resolve(Object pEmitterId)
    {
        return TaskManagerAndroid.this.mEmittersById.get(pEmitterId);
    }

    @Override
    public <TResult> void execute(Task<TResult> pTask)
    {
        execute(pTask, null);
    }

    protected <TResult> void execute(Task<TResult> pTask, TaskContainerAndroid<?> pParentContainer)
    {
        if (pTask == null) throw TaskManagerException.invalidTask();

        TaskConfiguration lTaskConfig = mManagerConfig.resolveConfiguration(pTask);
        TaskContainerAndroid<TResult> lContainer = new TaskContainerAndroid<TResult>(pTask, lTaskConfig, pParentContainer);
        // Remember all running tasks.
        mContainers.add(lContainer);

        // Eventually run the background task.
        lTaskConfig.getExecutor().execute(lContainer);
    }

    @Override
    public <TResult> boolean listen(TaskResult<TResult> pTaskListener)
    {
        return true;
    }

    protected <TResult> boolean listen(TaskResult<TResult> pTaskListener, TaskContainerAndroid<?> pContainer)
    {
        return true;
    }

    @Override
    public void notifyProgress(final TaskProgress pProgress)
    {
        throw TaskManagerException.notCalledFromTask();
    }

    protected void notifyProgress(final TaskProgress pProgress, final TaskContainerAndroid<?> pContainer)
    {
        // Progress is always executed on the UI-Thread but sent from a non-UI-Thread.
        mUIQueue.post(new Runnable() {
            public void run()
            {
                if (pContainer.referenceEmitter()) {
                    try {
                        pProgress.onProgress(TaskManagerAndroid.this);
                    } finally {
                        pContainer.dereferenceEmitter();
                    }
                }
            }
        });
    }

    /**
     * Called when task computation has ended. It restores emitters reference if applicable and triggers the right task callbacks.
     * Triggers are executed on the UI Thread.
     * 
     * @param pContainer Container that contains the finished task.
     * @param pConfirmProcessed True to indicate that the task has finished its computation or False if we don't know. This
     *            parameter should be set to true only from the computation thread after onProcess(), when we know for task is
     *            over.
     */
    protected <TResult> void tryFinish(final TaskContainerAndroid<TResult> pContainer, final boolean pConfirmProcessed)
    {
        if (Thread.currentThread() != mUIThread) {
            mUIQueue.post(new Runnable() {
                public void run()
                {
                    tryFinish(pContainer, pConfirmProcessed);
                }
            });
        } else {
            // If we know for sure the task is finished, set the corresponding flag. That way, finish() can be called at any time:
            // - When task isn't finished yet, in which case nothing happens. This can occur if a new instance of the emitter
            // becomes managed while task is still executing: the task manager try to call finish on all tasks.
            // - When task has just finished, i.e. finish is called from the computation thread, and its emitter is available. In
            // this case, the flag is set to true and task final callback is triggered.
            // - When task has just finished but its emitter is not available yet, i.e. it has been unmanaged. In this case, the
            // flag is set to true but task final callback is NOT triggered. it will be triggered later when a new emitter (with
            // the same Id) becomes managed.
            // - When an emitter with the same Id as the previously managed-then-unmanaged one becomes managed. In this case the
            // flag is already true but the final task callback may have not been called yet (i.e. if mFinished is false). This
            // can be done now. Note hat it is possible to have finish() called several times since there may be a delay between
            // finish() call and execution as it is posted on the UI Thread.
            if (pContainer.finish(pConfirmProcessed)) {
                mContainers.remove(pContainer);
            }
        }
    }

    /**
     * TODO Explain why we implement TaskManager.
     * 
     * @param <TResult>
     */
    private class TaskContainerAndroid<TResult> implements Runnable, TaskManager
    {
        // Container info.
        private Task<TResult> mTask;
        private Object mTaskId;
        private TaskConfiguration mTaskConfig;
        private boolean mIsInner;
        private TaskContainerAndroid<?> mParentContainer;

        // Cached values.
        private Field mEmitterField;
        private Object mEmitterId;

        // Task result and state.
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mProcessed;
        private boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask, TaskConfiguration pTaskConfig, TaskContainerAndroid<?> pParentContainer)
        {
            super();
            mTask = pTask;
            mTaskId = (pTask instanceof TaskIdentity) ? ((TaskIdentity) pTask).getId() : null;
            mTaskConfig = pTaskConfig;
            mIsInner = (pTask.getClass().getEnclosingClass() != null);
            mParentContainer = pParentContainer;

            mEmitterField = resolveEmitterField();
            mEmitterId = manageEmitter();

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;
        }

        /**
         * Locate the outer object reference Field (e.g. this$0) inside the task class.
         * 
         * @param pTask Object on which the field must be located.
         * @return Field pointing to the outer object or null if pTask is not an inner-class.
         */
        private Field resolveEmitterField()
        {
            if (mIsInner) {
                Field[] lFields = mTask.getClass().getDeclaredFields();
                for (Field lField : lFields) {
                    String lFieldName = lField.getName();
                    if (lFieldName.startsWith("this$")) {
                        lField.setAccessible(true);
                        return lField;
                    }
                }
                throw TaskManagerException.invalidTask(mTask, "Could not find outer class field.");
            } else {
                return null;
            }
        }

        /**
         * Find the outer object owning the task and dereference it. Turn it into an object managed by the TaskManager.
         * 
         * @param pTask Object on which the field must be located.
         * @return Field pointing to the outer object or null if pTask is not an inner-class.
         */
        private Object manageEmitter()
        {
            if (mIsInner) {
                Object lEmitter;
                try {
                    lEmitter = mEmitterField.get(mTask);
                    if (lEmitter == null) throw TaskManagerException.invalidTask(mTask, "Could not find outer class reference.");

                    // Remove emitter object reference located inside the task.
                    dereferenceEmitter();
                    // Save emitter object reference.
                    return TaskManagerAndroid.this.manage(lEmitter, this);
                } catch (IllegalArgumentException eIllegalArgumentException) {
                    throw TaskManagerException.internalError();
                } catch (IllegalAccessException eIllegalAccessException) {
                    throw TaskManagerException.internalError();
                }
            } else {
                return null;
            }
        }

        /**
         * Dereference the emitter (which is an outer class) from the task (which is an inner class) and return its Id. Emitter
         * references are stored internally so that they can be restored later when the task has finished its computation. Note
         * that an emitter Id can be null if a task is not an inner class or if no dereferencement should be applied.
         * 
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        protected final void dereferenceEmitter()
        {
            try {
                // Dereference the parent emitter.
                if (mParentContainer != null) mParentContainer.dereferenceEmitter();
                // Dereference emitter.
                if (mIsInner) {
                    mEmitterField.set(mTask, null);
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw TaskManagerException.internalError();
            } catch (IllegalAccessException eIllegalAccessException) {
                throw TaskManagerException.internalError();
            }
        }

        /**
         * Restore the emitter back into the task.
         * 
         * @param pContainer Container that contains the task to restore.
         * @return True if restoration could be performed properly. This may be false if a previously managed object become
         *         unmanaged meanwhile.
         */
        protected final boolean referenceEmitter()
        {
            try {
                if (mIsInner) {
                    // Check if we need and have a reference to the emitter.
                    // It is important to validate reference can be restored before trying to restore it in the parent container.
                    // Indeed, if the reference cannot be restored at one level in the hierarchy, then no emitter is restored at
                    // any level and thus, no "rollback" is necessary.
                    WeakReference<?> lEmitterRef = resolve(mEmitterId);
                    if (lEmitterRef == null) return false;
                    Object lEmitter = lEmitterRef.get();
                    if (lEmitter == null) return false;

                    // Check if parent containers can and have been restored.
                    if ((mParentContainer != null) && !mParentContainer.referenceEmitter()) return false;

                    // Finally restore the emitter reference.
                    mEmitterField.set(mTask, lEmitter);
                    return true;
                } else {
                    return (mParentContainer != null) ? mParentContainer.referenceEmitter() : true;
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw TaskManagerException.internalError();
            } catch (IllegalAccessException eIllegalAccessException) {
                throw TaskManagerException.internalError();
            }
        }

        /**
         * Run background task on Executor-thread
         */
        public void run()
        {
            try {
                mResult = mTask.onProcess(this);
            } catch (final Exception eException) {
                mThrowable = eException;
            } finally {
                TaskManagerAndroid.this.tryFinish(this, true);
            }
        }

        /**
         * Execute task termination handlers (i.e. onFinish and onFail).
         * 
         * @param pConfirmProcessed True to indicate that the task has finished its computation or False if we don't know.
         * @return True if the task could be finished and its termination handlers executed or false otherwise.
         */
        protected boolean finish(boolean pConfirmProcessed)
        {
            if (pConfirmProcessed) mProcessed = true;

            // Execute task termination handlers if they have not been yet (but only if the task has been fully executed).
            if (!mProcessed || mFinished) return false;
            // Try to restore the emitter reference. If we can't, ask the configuration what to do.
            if (!referenceEmitter() && mTaskConfig.keepResultOnHold()) return false;

            try {
                if (mThrowable == null) {
                    try {
                        mTask.onFinish(this, mResult);
                    } catch (Exception eException) {
                        mTask.onFail(this, eException);
                    }
                } else {
                    mTask.onFail(this, mThrowable);
                }
            }
            // An exception occurred inside onFail. We can't do much now except committing a suicide or logging the exception
            // and then ignoring it. I tend to prefer an explicit error even if it kills the application... That's a choice
            // that should be re-thought. Anyway this explains why there is no catch here.
            finally {
                mFinished = true;
            }
            return true;
        }

        @Override
        public void manage(Object pEmitter)
        {
            TaskManagerAndroid.this.manage(pEmitter);
        }

        @Override
        public void unmanage(Object pEmitter)
        {
            TaskManagerAndroid.this.unmanage(pEmitter);
        }

        @Override
        public <TOtherResult> void execute(Task<TOtherResult> pTask)
        {
            TaskManagerAndroid.this.execute(pTask, this);
        }

        @Override
        public <TOtherResult> boolean listen(TaskResult<TOtherResult> pTaskListener)
        {
            return TaskManagerAndroid.this.listen(pTaskListener, this);
        }

        @Override
        public void notifyProgress(TaskProgress pProgress)
        {
            TaskManagerAndroid.this.notifyProgress(pProgress, this);
        }

        @Override
        public boolean equals(Object pOther)
        {
            if (mTaskId != null) {
                return mTaskId.equals(pOther);
            } else {
                return super.equals(pOther);
            }
        }

        @Override
        public int hashCode()
        {
            if (mTaskId != null) {
                return mTaskId.hashCode();
            } else {
                return super.hashCode();
            }
        }
    }

    /**
     * Not thread-safe.
     */
    public interface ManagerConfiguration
    {
        Object resolveEmitterId(Object pEmitter);

        TaskConfiguration resolveConfiguration(Task<?> pTask);
    }

    /**
     * Not thread-safe.
     */
    public interface TaskConfiguration
    {
        ExecutorService getExecutor();

        boolean keepResultOnHold();
    }
}
