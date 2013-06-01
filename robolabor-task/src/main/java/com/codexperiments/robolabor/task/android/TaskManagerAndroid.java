package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.TaskManagerException.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentifiable;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;
import com.codexperiments.robolabor.task.id.TaskId;
import com.codexperiments.robolabor.task.id.TaskRef;

/**
 * TODO Handle timeout.
 * 
 * TODO Handle cancellation.
 * 
 * TODO Configuration option so that termination handlers don't crash the application if a runtime exception occur.
 * 
 * TODO Configuration option to forbid use of unmanaged objects.
 * 
 * TODO Configuration option to make a task reusable.
 * 
 * TODO onRestore
 */
public class TaskManagerAndroid implements TaskManager
{
    private static int TASK_ID_COUNTER;

    private ManagerConfiguration mManagerConfig;
    private Set<TaskContainer<?>> mContainers;
    private Map<Object, WeakReference<?>> mEmittersById;

    private Handler mUIQueue;
    private Looper mUILooper;

    static {
        TASK_ID_COUNTER = Integer.MIN_VALUE;
    }

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
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        if (manage(pEmitter, null) != null) {
            // Try to terminate any task that could be terminated now that a new "potential emitter" is registered.
            for (TaskContainer<?> lContainer : mContainers) {
                if (lContainer.finish()) {
                    notifyFinished(lContainer);
                }
            }
        }
    }

    protected Object manage(Object pEmitter, TaskContainer<?> pParentContainer)
    {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");

        // Save the new task emitter in the reference list. Replace the existing one if any according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. See DefaultConfiguration for an example.
        // Note that an emitter Id can be null if no emitter dereferencement should be applied.
        Object lEmitterId = mManagerConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            // If user has requested explicitly to manage the emitter, then just obey!
            if (pParentContainer == null) {
                // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
                // collector do its job.
                mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
            }
            // If the present method is invoked internally from prepareToRun(),
            else {
                // TODO emitter id must not be a the emitter itself. Should find a way to forbid it.
                // If emitter is managed by the user explicitly, do nothing. User can update reference through manage(Object).
                if (mEmittersById.get(lEmitterId) == null) {
                    mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
                }
            }
            // 1st condition: If user has requested explicitly to manage the emitter (by calling managed(Object)), then just obey!
            // 2nd condition: If the present method is invoked internally from prepareToRun() (i.e. pParentContainer != null) and:
            // If emitter is managed by the user explicitly (i.e. emitter is already present in the emitter list), do nothing.
            // User can update reference himself through manage(Object).
            // If emitter is not managed by the user explicitly but is already present in the emitter list because another task
            // has been executed, do nothing. Indeed unmanaged emitter are unique and never need to be updated.
            // If emitter is not managed by the user explicitly but is not present in the emitter list, then start managing it so
            // that we will be able to restore its reference later.
            // TODO emitter id must not be a the emitter itself. Should find a way to forbid it.
            if ((pParentContainer == null) || (mEmittersById.get(lEmitterId) == null)) {
                // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
                // collector do its job.
                mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
            }
        }
        return lEmitterId;
    }

    @Override
    public void unmanage(Object pEmitter)
    {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();

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
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask)
    {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        return execute(pTask, null);
    }

    protected <TResult> TaskRef<TResult> execute(Task<TResult> pTask, TaskContainer<?> pParentContainer)
    {
        if (pTask == null) throw new NullPointerException("Task is null");

        // Create a container to run the task.
        TaskConfiguration lTaskConfig = mManagerConfig.resolveConfiguration(pTask);
        TaskContainer<TResult> lContainer = new TaskContainer<TResult>(pTask, lTaskConfig, pParentContainer, mUIQueue);
        // Remember and run the new task. If an identical task is already executing, do nothing to prevent duplicate tasks.
        if (!mContainers.contains(lContainer)) {
            // Prepare the task (i.e. cache needed values) before adding it because the first operation can fail (and we don't
            // want to leave the container list in an incorrect state).
            TaskRef<TResult> lTaskRef = lContainer.prepareToRun();
            mContainers.add(lContainer);
            lTaskConfig.getExecutor().execute(lContainer);
            return lTaskRef;
        } else {
            return null;
        }
    }

    @Override
    public <TResult> boolean rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult)
    {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        return rebind(pTaskRef, pTaskResult, null);
    }

    @SuppressWarnings("unchecked")
    protected <TResult> boolean rebind(TaskRef<TResult> pTaskRef,
                                       TaskResult<TResult> pTaskResult,
                                       TaskContainer<?> pParentContainer)
    {
        if (pTaskRef == null) throw new NullPointerException("Task is null");
        if (pTaskResult == null) throw new NullPointerException("TaskResult is null");

        for (TaskContainer<?> lContainer : mContainers) {
            if (lContainer.hasSameRef(pTaskRef)) {
                // Cast safety is guaranteed by the execute() method that returns a properly typed TaskId for the new container.
                ((TaskContainer<TResult>) lContainer).switchHandler(pTaskResult, pParentContainer);
                return true;
            }
        }
        return false;
    }

    @Override
    public void notifyProgress(final TaskProgress pProgress)
    {
        throw notCalledFromTask();
    }

    /**
     * Called when task is processed and finished to clean remaining references.
     * 
     * @param pContainer Finished task container.
     */
    protected void notifyFinished(final TaskContainer<?> pContainer)
    {
        mContainers.remove(pContainer);
    }


    /**
     * TODO Explain why we implement TaskManager.
     */
    private class TaskContainer<TResult> implements Runnable, TaskManager
    {
        // Handlers
        private Task<TResult> mTask;
        private TaskResult<TResult> mTaskResult;

        // Container info.
        private TaskRef<TResult> mTaskRef;
        private TaskId mTaskId;
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
        private List<TaskEmitter> mEmitters;
        private Runnable mProgressRunnable;

        public TaskContainer(Task<TResult> pTask,
                             TaskConfiguration pTaskConfig,
                             TaskContainer<?> pParentContainer,
                             Handler pUIQueue)
        {
            super();
            mTask = pTask;
            mTaskResult = pTask;

            mTaskRef = new TaskRef<TResult>(TASK_ID_COUNTER++);
            mTaskId = (pTask instanceof TaskIdentifiable) ? ((TaskIdentifiable) pTask).getId() : null;
            mTaskConfig = pTaskConfig;
            mIsInner = false;
            mParentContainer = pParentContainer;
            mUIQueue = pUIQueue;

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;

            mEmitters = new ArrayList<TaskEmitter>(1); // Most of the time, a task will have only one emitter.
            mProgressRunnable = null;
        }

        /**
         * TODO Comment Locate all the outer object references (e.g. this$0) inside the task class. Check is performed recursively
         * on all super classes too.
         */
        protected TaskRef<TResult> prepareToRun()
        {
            mEmitters.clear();

            // Go through the main class and each of its super classes and look for "this$" fields.
            Class<?> lTaskClass = mTaskResult.getClass();
            while (lTaskClass != Object.class) {
                // If current class is an inner class...
                if ((lTaskClass.getEnclosingClass() != null) && !Modifier.isStatic(lTaskClass.getModifiers())) {
                    mIsInner = true;

                    // Find emitter references and manage them.
                    for (Field lField : lTaskClass.getDeclaredFields()) {
                        if (lField.getName().startsWith("this$")) {
                            prepare(lField);
                            // There should be only one outer reference per "class" in the Task class hierarchy. So we can stop as
                            // soon as the field is found as there won't be another.
                            break;
                        }
                    }
                }
                lTaskClass = lTaskClass.getSuperclass();
            }
            return mTaskRef;
        }

        /**
         * Manage the emitter referenced from the given field, i.e. save a weak reference pointing to it and keep its Id from
         * within the container.
         * 
         * @param pField Field to manage.
         */
        private void prepare(Field pField)
        {
            try {
                pField.setAccessible(true);

                // Retrieve the emitter by extracting it "reflectively" from the task field and compute its Id.
                Object lEmitterId = null;
                Object lEmitter = pField.get(mTaskResult);
                if (lEmitter != null) {
                    lEmitterId = TaskManagerAndroid.this.manage(lEmitter, this);
                }
                // If reference is null, that means the emitter is probably used in a parent container and
                // already managed. Try to find its Id in parent containers.
                else {
                    TaskContainer<?> lParentContainer = mParentContainer;
                    while ((lEmitterId == null) && (lParentContainer != null)) {
                        for (TaskEmitter lParentEmitter : lParentContainer.mEmitters) {
                            if (pField.getType() == lParentEmitter.mEmitterField.getType()) {
                                lEmitterId = lParentEmitter.mEmitterId;
                                break;
                            }
                        }
                        lParentContainer = lParentContainer.mParentContainer;
                    }
                }

                if (lEmitterId != null) {
                    mEmitters.add(new TaskEmitter(pField, lEmitterId));
                } else {
                    throw emitterIdCouldNotBeBound(mTaskResult);
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        /**
         * Dereference the emitter (which is an outer object) from the task (which is an inner class) and return its Id. Emitter
         * references are stored internally so that they can be restored later when the task has finished its computation. Note
         * that an emitter Id can be null if a task is not an inner class or if no dereferencement should be applied.
         * 
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        private void dereferenceEmitter()
        {
            try {
                // Dereference the parent emitter.
                if (mParentContainer != null) mParentContainer.dereferenceEmitter();
                // Dereference emitter.
                if (mIsInner) {
                    for (TaskEmitter lEmitter : mEmitters) {
                        lEmitter.mEmitterField.set(mTaskResult, null);
                    }
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        /**
         * Restore the emitter back into the task.
         * 
         * @param pContainer Container that contains the task to restore.
         * @return True if restoration could be performed properly. This may be false if a previously managed object become
         *         unmanaged meanwhile.
         */
        private boolean referenceEmitter()
        {
            try {
                // Try to restore emitters in parent containers.
                boolean lRestored = (mParentContainer == null) || mParentContainer.referenceEmitter();

                // Restore references for current container.
                if (mIsInner) {
                    for (TaskEmitter lEmitterDescriptor : mEmitters) {
                        WeakReference<?> lEmitterRef = TaskManagerAndroid.this.resolve(lEmitterDescriptor.mEmitterId);
                        Object lEmitter = null;
                        if (lEmitterRef != null) {
                            lEmitter = lEmitterRef.get();
                            lEmitterDescriptor.mEmitterField.set(mTaskResult, lEmitter);
                            // TODO If mTask != mTaskResult?
                        }
                        lRestored &= (lEmitter != null);
                    }
                }
                return lRestored;
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        /**
         * Run background task on Executor-thread
         */
        public void run()
        {
            try {
                // TODO We should find a way not to postpone the following operation.
                mUIQueue.post(new Runnable() {
                    public void run()
                    {
                        dereferenceEmitter();
                    }
                });
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
         * TODO Comments
         * 
         * @param pTaskResult
         */
        protected void switchHandler(TaskResult<TResult> pTaskResult, TaskContainer<?> pParentContainer)
        {
            mTaskResult = pTaskResult;
            mParentContainer = pParentContainer;
            prepareToRun();
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
                    mTaskResult.onFinish(this, mResult);
                } else {
                    mTaskResult.onFail(this, mThrowable);
                }
            }
            // An exception occurred inside onFail. We can't do much now except committing a suicide or logging the exception
            // and then ignoring it. I've chosen the first option but maybe decision should be left to the configuration...
            catch (RuntimeException eRuntimeException) {
                // An exception occurred inside onFail. We can't do much now except committing a suicide or logging the exception
                // and then ignoring it. I've chosen the first option but maybe decision should be left to the configuration...
                // Log.e(TaskManagerAndroid.class.getSimpleName(), "Exception raised inside task handler", eRuntimeException);
                throw eRuntimeException;
            } finally {
                // After task is over, it may still get dereferenced (e.g. if a child task gets executed). So dereference it
                // immediately to make the detection of potential NullPointerException (e.g. because of an inner class) easier.
                dereferenceEmitter();
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
        public <TOtherResult> TaskRef<TOtherResult> execute(Task<TOtherResult> pTask)
        {
            return TaskManagerAndroid.this.execute(pTask, this);
        }

        @Override
        public <TOtherResult> boolean rebind(TaskRef<TOtherResult> pTaskId, TaskResult<TOtherResult> pTaskResult)
        {
            return TaskManagerAndroid.this.rebind(pTaskId, pTaskResult, this);
        }

        @Override
        public void notifyProgress(final TaskProgress pProgress)
        {
            if (pProgress == null) throw new NullPointerException("Progress is null");

            Runnable lProgressRunnable;
            // @violations off: Optimization to avoid allocating a runnable each time progress is handled from the task itself.
            if ((pProgress == mTask) && (mProgressRunnable != null)) { // @violations on
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
                // @violations off : Optimization that caches progress runnable if progress is handled from the task itself.
                if (pProgress == mTask) mProgressRunnable = lProgressRunnable; // @violations on
            }

            // Progress is always executed on the UI-Thread but sent from a non-UI-Thread (except if calledfrom onFinish() or
            // onFail() but that shouldn't occur often).
            mUIQueue.post(lProgressRunnable);
        }

        public boolean hasSameRef(TaskRef<?> pTaskRef)
        {
            return mTaskRef.equals(pTaskRef);
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
                throw internalError();
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
                throw internalError();
            }
        }
    }


    // TODO TaskEmitter should be renamed to something like TaskEmitterDescriptor
    private static class TaskEmitter
    {
        private Field mEmitterField;
        private Object mEmitterId;

        public TaskEmitter(Field pEmitterField, Object pEmitterId)
        {
            mEmitterField = pEmitterField;
            mEmitterId = pEmitterId;
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