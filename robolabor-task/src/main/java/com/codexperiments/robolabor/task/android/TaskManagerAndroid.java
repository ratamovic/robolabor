package com.codexperiments.robolabor.task.android;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

/**
 * TODO handler = getWindow().getDecorView().getHandler();
 * 
 * TODO Handle Unique tasks.
 * 
 * TODO Handle timeout.
 * 
 * TODO Handle cancellation.
 * 
 * TODO Implement listen().
 * 
 * TODO Execute tasks from UI thread only.
 */
public class TaskManagerAndroid implements TaskManager
{
    private ManagerConfiguration mManagerConfig;
    private Set<TaskContainer<?>> mContainers;
    private Map<Object, WeakReference<?>> mEmittersById;

    private Handler mUIQueue;
    private Looper mUILooper;

    public TaskManagerAndroid(ManagerConfiguration pConfig)
    {
        super();
        mManagerConfig = pConfig;
        mContainers = new HashSet<TaskContainer<?>>();
        mEmittersById = new HashMap<Object, WeakReference<?>>();

        mUILooper = Looper.getMainLooper();
        mUIQueue = new Handler(mUILooper);
    }

    @Override
    public void manage(Object pEmitter)
    {
        if (Looper.myLooper() != mUILooper) throw TaskManagerException.mustBeExecutedFromUIThread();
        manage(pEmitter, null);
    }

    protected Object manage(Object pEmitter, TaskContainer<?> pParentContainer)
    {
        // Save the new task emitter in the reference list. Replace the existing one if any according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. See DefaultConfiguration for an example.
        // Note that an emitter Id can be null if no emitter dereferencement should be applied.
        Object lEmitterId = mManagerConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
            // collector do its job.
            mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
            // Try to terminate any task that could be terminated now that a new "potential emitter" is registered.
            for (TaskContainer<?> lContainer : mContainers) {
                if (lContainer.finish()) {
                    notifyFinished(lContainer);
                }
            }
        }
        return lEmitterId;
    }

    @Override
    public void unmanage(Object pEmitter)
    {
        if (Looper.myLooper() != mUILooper) throw TaskManagerException.mustBeExecutedFromUIThread();
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
        return mEmittersById.get(pEmitterId);
    }

    @Override
    public <TResult> void execute(Task<TResult> pTask)
    {
        if (Looper.myLooper() != mUILooper) throw TaskManagerException.mustBeExecutedFromUIThread();
        execute(pTask, null);
    }

    protected <TResult> void execute(Task<TResult> pTask, TaskContainer<?> pParentContainer)
    {
        if (pTask == null) throw TaskManagerException.invalidTask();

        // Create a container to run the task.
        TaskConfiguration lTaskConfig = mManagerConfig.resolveConfiguration(pTask);
        TaskContainer<TResult> lContainer = new TaskContainer<TResult>(pTask, lTaskConfig, pParentContainer, mUIQueue);
        // Remember and run the new task. If an identical task is already executing, do nothing to prevent duplicate tasks.
        if (!mContainers.contains(lContainer)) {
            // Prepare the task (i.e. cache needed values) before adding it because the first operation can fail (and we don't
            // want to leave the container list in an incorrect state).
            lContainer.prepareToRun();
            mContainers.add(lContainer);
            lTaskConfig.getExecutor().execute(lContainer);
        }
    }

    @Override
    public <TResult> boolean listen(TaskResult<TResult> pTaskListener)
    {
        if (Looper.myLooper() != mUILooper) throw TaskManagerException.mustBeExecutedFromUIThread();
        return true;
    }

    protected <TResult> boolean listen(TaskResult<TResult> pTaskListener, TaskContainer<?> pContainer)
    {
        return true;
    }

    @Override
    public void notifyProgress(final TaskProgress pProgress)
    {
        throw TaskManagerException.notCalledFromTask();
    }

    /**
     * Called when task is processed and finished to clean remaining references.
     * 
     * @param pContainer Finished task container.
     */
    protected <TResult> void notifyFinished(final TaskContainer<TResult> pContainer)
    {
        mContainers.remove(pContainer);
    }

    /**
     * TODO Explain why we implement TaskManager.
     * 
     * TODO We have a problem here: technically speaking, an object can contain several outer references (i.e. several this$x),
     * with at most one reference per inheritance level. This case is rare but possible and not handled here. To handle it
     * properly, we should look for an emitter field on Task super classes too and keep a list of emitter fields instead of a
     * single one. The same goes for emitter ids of course.
     * 
     * @param <TResult> Expected task result.
     */
    private class TaskContainer<TResult> implements Runnable, TaskManager
    {
        // Container info.
        private Task<TResult> mTask;
        private Object mTaskId;
        private TaskConfiguration mTaskConfig;
        private boolean mIsInner;
        private TaskContainer<?> mParentContainer;
        private Handler mUIQueue;

        // Task result and state.
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mProcessed;
        private boolean mFinished;

        // Cached values.
        private Field mEmitterField;
        private Object mEmitterId;
        private Runnable mProgressRunnable;

        public TaskContainer(Task<TResult> pTask,
                             TaskConfiguration pTaskConfig,
                             TaskContainer<?> pParentContainer,
                             Handler pUIQueue)
        {
            super();
            mTask = pTask;
            mTaskId = (pTask instanceof TaskIdentity) ? ((TaskIdentity) pTask).getId() : null;
            mTaskConfig = pTaskConfig;
            mIsInner = (pTask.getClass().getEnclosingClass() != null && !Modifier.isStatic(pTask.getClass().getModifiers()));
            mParentContainer = pParentContainer;
            mUIQueue = pUIQueue;

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;

            mEmitterField = null;
            mEmitterId = null;
            mProgressRunnable = null;
        }

        public void prepareToRun()
        {
            if (mIsInner) {
                try {
                    mEmitterField = resolveEmitterField();

                    // Find the outer object owning the task and dereference it. Turn it into a managed object.
                    Object lEmitter = mEmitterField.get(mTask);
                    if (lEmitter == null) {
                        throw TaskManagerException.invalidTask(mTask, "Could not find outer object reference.");
                    }
                    dereferenceEmitter();
                    mEmitterId = TaskManagerAndroid.this.manage(lEmitter, this);
                } catch (IllegalArgumentException eIllegalArgumentException) {
                    throw TaskManagerException.internalError();
                } catch (IllegalAccessException eIllegalAccessException) {
                    throw TaskManagerException.internalError();
                }

            }
        }

        /**
         * Locate the outer object reference Field (e.g. this$0) inside the task class. Check is performed recursively on super
         * classes until reference is found.
         * 
         * @param pTask Object on which the field must be located.
         * @return Field pointing to the outer objec
         * @throws TaskManagerException If pTask is not an inner-class or does not have an outer object reference (which should be
         *             impossible for an inner class).
         */
        private Field resolveEmitterField()
        {
            Class<?> lTaskClass = mTask.getClass();
            while (lTaskClass != null) {
                Field[] lFields = mTask.getClass().getDeclaredFields();
                for (Field lField : lFields) {
                    String lFieldName = lField.getName();
                    if (lFieldName.startsWith("this$")) {
                        lField.setAccessible(true);
                        return lField;
                    }
                }

                lTaskClass = lTaskClass.getSuperclass();
            }
            throw TaskManagerException.invalidTask(mTask, "Could not find outer class field.");
        }

        /**
         * Dereference the emitter (which is an outer class) from the task (which is an inner class) and return its Id. Emitter
         * references are stored internally so that they can be restored later when the task has finished its computation. Note
         * that an emitter Id can be null if a task is not an inner class or if no dereferencement should be applied.
         * 
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        private final void dereferenceEmitter()
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
        private final boolean referenceEmitter()
        {
            try {
                if (mIsInner) {
                    // Check if we need and have a reference to the emitter.
                    // It is important to validate reference can be restored before trying to restore it in the parent container.
                    // Indeed, if the reference cannot be restored at one level in the hierarchy, then no emitter is restored at
                    // any level and thus, no "rollback" is necessary.
                    WeakReference<?> lEmitterRef = TaskManagerAndroid.this.resolve(mEmitterId);
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
                mUIQueue.post(new Runnable() {
                    public void run()
                    {
                        mProcessed = true;
                        if (finish()) {
                            TaskManagerAndroid.this.notifyFinished(TaskContainer.this);
                        }
                    }
                });
            }
        }

        /**
         * Try to execute task termination handlers (i.e. onFinish and onFail). The latter may not be executed if at least one of
         * the outer object reference cannot be restored. When the task is effectively finished, set the corresponding flag to
         * prevent any other invokation. That way, finish() can be called at any time:
         * <ul>
         * <li>When task isn't finished yet, in which case nothing happens. This can occur if a new instance of the emitter
         * becomes managed while task is still executing: the task manager try to call finish on all tasks.</li>
         * <li>When task has just finished, i.e. finish is called from the computation thread, and its emitter is available. In
         * this case, the flag is set to true and task final callback is triggered.</li>
         * <li>When task has just finished but its emitter is not available yet, i.e. it has been unmanaged. In this case, the
         * flag is set to true but task final callback is NOT triggered. it will be triggered later when a new emitter (with the
         * same Id) becomes managed.</li>
         * <li>When an emitter with the same Id as the previously managed-then-unmanaged one becomes managed. In this case the
         * flag is already true but the final task callback may have not been called yet (i.e. if mFinished is false). This can be
         * done now. Note hat it is possible to have finish() called several times since there may be a delay between finish()
         * call and execution as it is posted on the UI Thread.</li>
         * </ul>
         * 
         * @return True if the task could be finished and its termination handlers executed or false otherwise.
         */
        protected boolean finish()
        {
            // Execute task termination handlers if they have not been yet (but only if the task has been fully processed).
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
            // and then ignoring it. I've chosen the first option but maybe decision should be left to the configuration...
            catch (RuntimeException eRuntimeException) {
                Log.e(TaskManagerAndroid.class.getSimpleName(), "An error occured inside task handler", eRuntimeException);
                throw eRuntimeException;
            } finally {
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
        public void notifyProgress(final TaskProgress pProgress)
        {
            Runnable lProgressRunnable;
            // Optimization to avoid allocating a runnable each time we want to handle progress from the task itself.
            if ((pProgress == mTask) && (mProgressRunnable != null)) {
                lProgressRunnable = mProgressRunnable;
            }
            // Create a runnable to handle task progress and reference/dereference properly the task before/after task execution.
            else {
                lProgressRunnable = new Runnable() {
                    public void run()
                    {
                        if (referenceEmitter()) {
                            try {
                                pProgress.onProgress(TaskManagerAndroid.this);
                            } finally {
                                dereferenceEmitter();
                            }
                        }
                    }
                };
                // Cache progress runnable if progress is handled from the task itself.
                if (pProgress == mTask) mProgressRunnable = lProgressRunnable;
            }

            // Progress is always executed on the UI-Thread but sent from a non-UI-Thread (except if calledfrom onFinish() or
            // onFail() but that shouldn't occur often).
            mUIQueue.post(lProgressRunnable);
        }

        @Override
        public boolean equals(Object pOther)
        {
            if (this == pOther) return true;
            if (pOther == null) return false;
            if (getClass() != pOther.getClass()) return false;

            TaskContainer<?> lOtherContainer = (TaskContainer<?>) pOther;
            // Check equality on the user-defined task Id if possible.
            if (mTaskId != null) {
                return mTaskId.equals(lOtherContainer.mTaskId);
            }
            // If the task has no Id, then we use task equality method. This is likely to turn into a simple reference check.
            else if (mTask != null) {
                return mTask.equals(lOtherContainer.mTask);
            }
            // A container cannot be created with a null task. So the following case should never occur.
            else {
                throw TaskManagerException.internalError();
            }
        }

        @Override
        public int hashCode()
        {
            if (mTaskId != null) {
                return mTaskId.hashCode();
            } else if (mTask != null) {
                return mTask.hashCode();
            } else {
                throw TaskManagerException.internalError();
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
