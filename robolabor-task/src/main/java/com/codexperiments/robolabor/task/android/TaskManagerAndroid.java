package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentifiable;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskManagerConfig;
import com.codexperiments.robolabor.task.TaskManagerException;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;
import com.codexperiments.robolabor.task.id.TaskId;
import com.codexperiments.robolabor.task.id.TaskRef;

/**
 * TODO Handle cancellation.
 * 
 * TODO onBeforeProcess / onRestore / onCommit
 * 
 * TODO Save TaskRefs list.
 * 
 * TODO Move Configuration to an external file.
 */
public class TaskManagerAndroid implements TaskManager
{
    // To generate task references.
    private static int TASK_REF_COUNTER;

    private Application mApplication;
    private Intent mServiceIntent;
    private Handler mUIQueue;
    private Looper mUILooper;

    private TaskManagerConfig mConfig;
    // All the current running tasks.
    private Set<TaskContainer<?>> mContainers;
    // Keep tracks of all emitters.
    private Map<TaskEmitterId, WeakReference<?>> mEmittersById;

    // Some dereferencing operations need to be post-poned.
    private boolean mPostPoneDereferencing;
    private List<TaskContainer<?>> mPostPonedContainers;

    static {
        TASK_REF_COUNTER = Integer.MIN_VALUE;
    }

    public TaskManagerAndroid(Application pApplication, TaskManagerConfig pConfig)
    {
        super();

        mApplication = pApplication;
        mServiceIntent = new Intent(mApplication, TaskManagerServiceAndroid.class);
        mUILooper = Looper.getMainLooper();
        mUIQueue = new Handler(mUILooper);

        mConfig = pConfig;
        mContainers = new HashSet<TaskContainer<?>>();
        mEmittersById = new HashMap<TaskEmitterId, WeakReference<?>>();

        mPostPoneDereferencing = false;
        mPostPonedContainers = new ArrayList<TaskManagerAndroid.TaskContainer<?>>();
    }

    @Override
    public void manage(Object pEmitter)
    {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        if (pEmitter == null) throw new NullPointerException("Emitter is null");

        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. Note that an emitter Id can be null if no
        // emitter dereferencing should be applied.
        Object lEmitterId = mConfig.resolveEmitterId(pEmitter);
        // Emitter id must not be the emitter itself or we have a leak. Warn user about this (tempting) configuration misuse.
        if ((lEmitterId == null) || (lEmitterId == pEmitter)) throw invalidEmitterId(lEmitterId, pEmitter);

        // Save the reference of the emitter. A weak reference is used to avoid memory leaks.
        TaskEmitterId lTaskEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterId);
        mEmittersById.put(lTaskEmitterId, new WeakReference<Object>(pEmitter));

        // Try to terminate any task we can, which is possible if the new registered object is one of their emitter.
        for (TaskContainer<?> lContainer : mContainers) {
            if (lContainer.finish()) {
                notifyFinished(lContainer);
            }
        }
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
        Object lEmitterId = mConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            TaskEmitterId lTaskEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterId); // TODO Cache to avoid an alloc?
            WeakReference<?> lWeakRef = mEmittersById.get(lTaskEmitterId);
            if ((lWeakRef != null) && (lWeakRef.get() == pEmitter)) {
                mEmittersById.remove(lTaskEmitterId);
            }
        }
    }

    /**
     * Called internally from prepareToRun(). Find the Id of the emitter. If the object is not already managed, then manage it.
     * 
     * @param pEmitter Emitter to find the Id of.
     * @return Emitter Id
     * @throws TaskManagerException If the emitter isn't managed (managed by configuration but manage() not called yet).
     */
    protected TaskEmitterId resolveId(Object pEmitter)
    {
        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. Note that an emitter Id can be null if no
        // emitter dereferencing should be applied.
        Object lEmitterId = mConfig.resolveEmitterId(pEmitter);
        // Emitter id must not be the emitter itself or we have a leak. Warn user about this (tempting) configuration misuse.
        // Note that when we arrive here, pEmitter cannot be null.
        if (lEmitterId == pEmitter) throw invalidEmitterId(lEmitterId, pEmitter);

        WeakReference<Object> lEmitterRef = new WeakReference<Object>(pEmitter);
        // An unmanaged object doesn't have any Id defined in the configuration, so create a unique one. For unmanaged objects,
        // which are unique "by reference", we use the WeakReference itself since it doesn't override Object.equals() (i.e. it is
        // unique "by reference" too). This is an optimization that could be perfectly replaced by something like "new Object()".
        TaskEmitterId lTaskEmitterId = new TaskEmitterId(pEmitter.getClass(), (lEmitterId != null) ? lEmitterId : lEmitterRef);
        // If emitter is managed by the user explicitly and is properly registered in the emitter list, do nothing. User can
        // update reference himself through manage(Object).
        // If emitter is managed (i.e. emitter Id returned by configuration) but is not in the emitter list, then a call to
        // manage() is missing. Throw an exception to warn the user.
        // If emitter is not managed by the user explicitly but is already present in the emitter list because another task
        // has been executed by the same emitter, do nothing. Indeed unmanaged emitter are unique and never need to be updated.
        // If emitter is not managed by the user explicitly but is not present in the emitter list, then start managing it so
        // that we will be able to restore its reference later (if it hasn't been garbage collected in-between).
        if (mEmittersById.get(lTaskEmitterId) == null) {
            // Managed emitter case.
            if (lEmitterId != null) {
                throw emitterNotManaged(lEmitterId, pEmitter);
            }
            // Unmanaged emitter case.
            else {
                if (mConfig.allowUnmanagedEmitters()) {
                    mEmittersById.put(lTaskEmitterId, lEmitterRef);
                } else {
                    throw unmanagedEmittersNotAllowed(pEmitter);
                }
            }
        }
        return lTaskEmitterId;
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
    protected WeakReference<?> resolveEmitter(TaskEmitterId pEmitterId)
    {
        return mEmittersById.get(pEmitterId);
    }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask)
    {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        return execute(pTask, pTask, null);
    }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask, TaskResult<TResult> pTaskResult)
    {
        return execute(pTask, pTaskResult, null);
    }

    protected <TResult> TaskRef<TResult> execute(Task<TResult> pTask,
                                                 TaskResult<TResult> pTaskResult,
                                                 TaskContainer<?> pParentContainer)
    {
        if (pTask == null) throw new NullPointerException("Task is null");

        // Create a container to run the task.
        TaskContainer<TResult> lContainer = new TaskContainer<TResult>(pTask, pTaskResult, mConfig, pParentContainer);
        // Remember and run the new task. If an identical task is already executing, do nothing to prevent duplicate tasks.
        if (!mContainers.contains(lContainer)) {
            // Prepare the task (i.e. cache needed values) before adding it because the first operation can fail (and we don't
            // want to leave the container list in an incorrect state).
            TaskRef<TResult> lTaskRef = lContainer.prepareToRun();
            mContainers.add(lContainer);
            mConfig.resolveExecutor(pTask).execute(lContainer);
            // Start the (empty) service to tell the system that TaskManager is running and application shouldn't be killed if
            // possible until all enqueued tasks are running. This is just a suggestion of course...
            if (mApplication.startService(mServiceIntent) == null) {
                throw serviceNotDeclaredInManifest();
            }
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
                // If Rebound task is over, execute newly attached handler.
                if (lContainer.finish()) {
                    notifyFinished(lContainer);
                }
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
        // All enqueued tasks are over. We can stop the (empty) service to tell Android nothing more is running.
        if (mContainers.isEmpty()) {
            mApplication.stopService(mServiceIntent);
        }
    }

    /**
     * Indicate that dereferencing operations need to be post-poned. Dereferencing can be performed immediately only if task is
     * not executed from another task, or else the latter, for example, could be dereferenced before it has finished executing its
     * termination handlers.
     */
    protected void postPoneDereferencing()
    {
        mPostPoneDereferencing = true;
    }

    /**
     * Try to dereference a container immediately if possible but post-pone the operation if necessary.
     * 
     * @param pTaskContainer Container to dereference.
     */
    protected void needDereference(TaskContainer<?> pTaskContainer)
    {
        if (mPostPoneDereferencing) {
            mPostPonedContainers.add(pTaskContainer);
        } else {
            pTaskContainer.dereferenceEmitter();
        }
    }

    /**
     * Immediately dereference the container and any pending dereferencing operations.
     * 
     * @param pTaskContainer Container to dereference.
     */
    protected void dereferenceContainer(TaskContainer<?> pTaskContainer)
    {
        mPostPoneDereferencing = false;
        for (TaskContainer<?> lContainer : mPostPonedContainers) {
            lContainer.dereferenceEmitter();
        }
        pTaskContainer.dereferenceEmitter();
        mPostPonedContainers.clear();
    }


    /**
     * Contains all the information about the task to execute: its Id, its handlers, a few cached values and the result or
     * exception... Most of the task execution logic is implemented here (referencing, dereferencing, etc.).
     * 
     * Note that TaskManager is implemented here because when a new task is executed from one other, then we need to keep track of
     * the context in which it is executed to remove or restore references properly. Thus, TaskManager methods called here are
     * just forwarded to the real TaskManager but with contextual information.
     */
    private class TaskContainer<TResult> implements Runnable, TaskManager
    {
        // Handlers
        private Task<TResult> mTask;
        private TaskResult<TResult> mTaskResult;

        // Container info.
        private TaskRef<TResult> mTaskRef;
        private TaskId mTaskId;
        private TaskManagerConfig mConfig;
        private TaskContainer<?> mParentContainer;

        // Task result and state.
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mProcessed;
        private boolean mFinished;

        // Cached values.
        private List<TaskEmitterDescriptor> mEmitterDescriptors;
        private Runnable mProgressRunnable;

        public TaskContainer(Task<TResult> pTask,
                             TaskResult<TResult> pTaskResult,
                             TaskManagerConfig pConfig,
                             TaskContainer<?> pParentContainer)
        {
            super();
            mTask = pTask;
            mTaskResult = pTaskResult;

            mTaskRef = new TaskRef<TResult>(TASK_REF_COUNTER++);
            mTaskId = (pTask instanceof TaskIdentifiable) ? ((TaskIdentifiable) pTask).getId() : null;
            mConfig = pConfig;
            mParentContainer = pParentContainer;

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;

            mEmitterDescriptors = new ArrayList<TaskEmitterDescriptor>(1); // Most of the time, a task will have only one emitter.
            mProgressRunnable = null;
        }

        /**
         * Initialize the container (i.e. cache needed values, ...) before running it.
         */
        protected TaskRef<TResult> prepareToRun()
        {
            mEmitterDescriptors.clear();

            if (mTask != mTaskResult) {
                prepareTask();
            }
            prepareTaskResult();

            needDereference(this);
            return mTaskRef;
        }

        /**
         * Dereference the task itself it is disjoint from its handlers. This is definitive.
         */
        private void prepareTask()
        {
            try {
                Class<?> lTaskClass = mTask.getClass();
                while (lTaskClass != Object.class) {
                    // If current class is an inner class...
                    if ((lTaskClass.getEnclosingClass() != null) && !Modifier.isStatic(lTaskClass.getModifiers())) {
                        if (!mConfig.allowInnerTasks()) throw innerTasksNotAllowed(mTask);

                        // Remove any references to the outer class.
                        for (Field lField : lTaskClass.getDeclaredFields()) {
                            if (lField.getName().startsWith("this$")) {
                                lField.setAccessible(true);
                                lField.set(mTask, null);
                                // There should be only one outer reference per "class" in the Task class hierarchy. So we can
                                // stop as soon as the field is found as there won't be another.
                                break;
                            }
                        }
                    }
                    lTaskClass = lTaskClass.getSuperclass();
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        /**
         * Locate all the outer object references (e.g. this$0) inside the task class, manage them if necessary and cache emitter
         * field properties for later use. Check is performed recursively on all super classes too.
         */
        private void prepareTaskResult()
        {
            // Go through the main class and each of its super classes and look for "this$" fields.
            Class<?> lTaskResultClass = mTaskResult.getClass();
            while (lTaskResultClass != Object.class) {
                // If current class is an inner class...
                if ((lTaskResultClass.getEnclosingClass() != null) && !Modifier.isStatic(lTaskResultClass.getModifiers())) {
                    // Find emitter references and manage them.
                    for (Field lField : lTaskResultClass.getDeclaredFields()) {
                        if (lField.getName().startsWith("this$")) {
                            prepareField(lField);
                            // There should be only one outer reference per "class" in the Task class hierarchy. So we can stop as
                            // soon as the field is found as there won't be another.
                            break;
                        }
                    }
                }
                lTaskResultClass = lTaskResultClass.getSuperclass();
            }
        }

        /**
         * Manage the emitter referenced from the given field, i.e. save a weak reference pointing to it and keep its Id from
         * within the container.
         * 
         * @param pField Field to manage.
         */
        private void prepareField(Field pField)
        {
            try {
                pField.setAccessible(true);

                // Extract the emitter "reflectively" and compute its Id.
                TaskEmitterId lEmitterId = null;
                Object lEmitter = pField.get(mTaskResult);
                if (lEmitter != null) {
                    lEmitterId = TaskManagerAndroid.this.resolveId(lEmitter);
                }
                // If reference is null, that means the emitter is probably used in a parent container and
                // already managed. Try to find its Id in parent containers.
                else {
                    TaskContainer<?> lParentContainer = mParentContainer;
                    while ((lEmitterId == null) && (lParentContainer != null)) {
                        for (TaskEmitterDescriptor lParentEmitterDescriptor : lParentContainer.mEmitterDescriptors) {
                            if (pField.getType() == lParentEmitterDescriptor.mEmitterField.getType()) {
                                lEmitterId = lParentEmitterDescriptor.mEmitterId;
                                break;
                            }
                        }
                        lParentContainer = lParentContainer.mParentContainer;
                    }
                }

                if (lEmitterId != null) {
                    mEmitterDescriptors.add(new TaskEmitterDescriptor(pField, lEmitterId));
                } else {
                    throw emitterIdCouldNotBeDetermined(mTaskResult);
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
         * that an emitter Id can be null if a task is not an inner class or if no dereferencing should be applied.
         * 
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        private void dereferenceEmitter()
        {
            try {
                if (mParentContainer != null) mParentContainer.dereferenceEmitter();
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    lEmitterDescriptor.mEmitterField.set(mTaskResult, null);
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
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    WeakReference<?> lEmitterRef = TaskManagerAndroid.this.resolveEmitter(lEmitterDescriptor.mEmitterId);
                    Object lEmitter = null;
                    if (lEmitterRef != null) {
                        lEmitter = lEmitterRef.get();
                        lEmitterDescriptor.mEmitterField.set(mTaskResult, lEmitter);
                    }
                    lRestored &= (lEmitter != null);
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
         * Replace the previous task handler (usually implemented in the task itself) with a new one. Previous handler is lost.
         * 
         * @param pTaskResult Task handler that must replace previous one.
         * @param pParentContainer Context in which the task handler is executed.
         */
        protected void switchHandler(TaskResult<TResult> pTaskResult, TaskContainer<?> pParentContainer)
        {
            mTaskResult = pTaskResult;
            mParentContainer = pParentContainer;
            mProgressRunnable = null;
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
            if (!referenceEmitter() && mConfig.keepResultOnHold(mTask)) {
                // Rollback any modification to leave container in a clean state.
                dereferenceContainer(this);
            } else {
                try {
                    postPoneDereferencing();
                    if (mThrowable == null) {
                        mTaskResult.onFinish(this, mResult);
                    } else {
                        mTaskResult.onFail(this, mThrowable);
                    }
                }
                // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
                catch (RuntimeException eRuntimeException) {
                    if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                } finally {
                    // After task is over, it may still get dereferenced (e.g. if a child task gets executed). So dereference it
                    // immediately to leave it in a clean state. This will ease potential NullPointerException detection (e.g. if
                    // an inner task is executed from termination handler of another task).
                    dereferenceContainer(this);
                    mFinished = true;
                }
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
            return TaskManagerAndroid.this.execute(pTask, pTask, this);
        }

        @Override
        public <TOtherResult> TaskRef<TOtherResult> execute(Task<TOtherResult> pTask, TaskResult<TOtherResult> pTaskResult)
        {
            return TaskManagerAndroid.this.execute(pTask, pTaskResult, this);
        }

        @Override
        public <TOtherResult> boolean rebind(TaskRef<TOtherResult> pTaskRef, TaskResult<TOtherResult> pTaskResult)
        {
            return TaskManagerAndroid.this.rebind(pTaskRef, pTaskResult, this);
        }

        @Override
        public void notifyProgress(final TaskProgress pTaskProgress)
        {
            if (pTaskProgress == null) throw new NullPointerException("Progress is null");

            Runnable lProgressRunnable;
            // @violations off: Optimization to avoid allocating a runnable each time progress is handled from the task itself.
            if ((pTaskProgress == mTaskResult) && (mProgressRunnable != null)) { // @violations on
                lProgressRunnable = mProgressRunnable;
            }
            // Create a runnable to handle task progress and reference/dereference properly the task before/after task execution.
            else {
                lProgressRunnable = new Runnable() {
                    public void run()
                    {
                        try {
                            postPoneDereferencing();
                            if (referenceEmitter()) {
                                pTaskProgress.onProgress(TaskManagerAndroid.this);
                            }
                        }
                        // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
                        catch (RuntimeException eRuntimeException) {
                            if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                        } finally {
                            dereferenceContainer(TaskContainer.this);
                        }
                    }
                };
                // @violations off : Optimization that caches progress runnable if progress is handled from the task itself.
                if (pTaskProgress == mTaskResult) mProgressRunnable = lProgressRunnable; // @violations on
            }

            // Progress is always executed on the UI-Thread but sent from a non-UI-Thread (except if called from onFinish() or
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


    /**
     * Contains all the information necessary to restore an emitter on a task handler (its field and its generated Id).
     */
    private static final class TaskEmitterDescriptor
    {
        private final Field mEmitterField;
        private final TaskEmitterId mEmitterId;

        public TaskEmitterDescriptor(Field pEmitterField, TaskEmitterId pEmitterId)
        {
            mEmitterField = pEmitterField;
            mEmitterId = pEmitterId;
        }

        @Override
        public String toString()
        {
            return "TaskEmitter [mEmitterField=" + mEmitterField + ", mEmitterId=" + mEmitterId + "]";
        }
    }


    /**
     * Contains the information to store the Id of an emitter. Emitter class is necessary since if internal Ids may be quite
     * common and thus similar between emitters of different types (e.g. fragments which have integer Ids starting from 0).
     */
    private static final class TaskEmitterId
    {
        private final Class<?> mType;
        private final Object mId;

        public TaskEmitterId(Class<?> pType, Object pId)
        {
            super();
            mType = pType;
            mId = pId;
        }

        @Override
        public boolean equals(Object pOther)
        {
            if (this == pOther) return true;
            if (pOther == null) return false;
            if (getClass() != pOther.getClass()) return false;

            TaskEmitterId lOther = (TaskEmitterId) pOther;
            if (mId == null) {
                if (lOther.mId != null) return false;
            } else if (!mId.equals(lOther.mId)) return false;

            if (mType == null) return lOther.mType == null;
            else return mType.equals(lOther.mType);
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mId == null) ? 0 : mId.hashCode());
            result = prime * result + ((mType == null) ? 0 : mType.hashCode());
            return result;
        }

        @Override
        public String toString()
        {
            return "TaskEmitterId [mType=" + mType + ", mId=" + mId + "]";
        }
    }

}