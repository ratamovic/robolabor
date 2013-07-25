package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.emitterIdCouldNotBeDetermined;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.emitterNotManaged;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.innerTasksNotAllowed;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.internalError;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.invalidEmitterId;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.mustBeExecutedFromUIThread;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.notCalledFromTask;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.serviceNotDeclaredInManifest;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.unmanagedEmittersNotAllowed;

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

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskManagerConfig;
import com.codexperiments.robolabor.task.TaskManagerException;
import com.codexperiments.robolabor.task.TaskRef;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;
import com.codexperiments.robolabor.task.handler.TaskStart;
import com.codexperiments.robolabor.task.id.TaskId;

/**
 * TODO Remove the need for contextual call.
 * 
 * TODO Remove TaskId but create a TaskEquality helper class.
 * 
 * TODO Handle cancellation.
 * 
 * TODO onBeforeProcess / onRestore / onCommit
 * 
 * TODO Save TaskRefs list.
 * 
 * TODO TaskRef add a Tag
 * 
 * TODO Add an onStart() handler.
 * 
 * TODO pending(TaskType)
 */
public class TaskManagerAndroid implements TaskManager {
    // To generate task references.
    private static int TASK_REF_COUNTER;

    private Application mApplication;
    private Handler mUIQueue;
    private Looper mUILooper;
    private Intent mServiceIntent;

    private TaskManagerConfig mConfig;
    // All the current running tasks.
    private Set<TaskContainer<?>> mContainers;
    // Keep tracks of all emitters.
    private Map<TaskEmitterId, TaskEmitterRef> mEmittersById;

    static {
        TASK_REF_COUNTER = Integer.MIN_VALUE;
    }

    public TaskManagerAndroid(Application pApplication, TaskManagerConfig pConfig) {
        super();

        mApplication = pApplication;
        mUILooper = Looper.getMainLooper();
        mUIQueue = new Handler(mUILooper);
        // Because a service is created, the client application dies if the UI-Thread stops for a long time (an ANR). This
        // shouldn't happen at runtime except when running in Debug mode with a breakpoint placed in the UI-Thread. Thus, service
        // is disabled in Debug mode. I don't think this is a good idea but for now this is the simplest thing to do.
        mServiceIntent = new Intent(mApplication, TaskManagerServiceAndroid.class);

        mConfig = pConfig;
        mContainers = new HashSet<TaskContainer<?>>();
        mEmittersById = new HashMap<TaskEmitterId, TaskEmitterRef>();
    }

    @Override
    public void manage(Object pEmitter) {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        if (pEmitter == null) throw new NullPointerException("Emitter is null");

        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. Note that an emitter Id can be null if no
        // emitter dereferencing should be applied.
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        // Emitter id must not be the emitter itself or we have a leak. Warn user about this (tempting) configuration misuse.
        if ((lEmitterIdValue == null) || (lEmitterIdValue == pEmitter)) throw invalidEmitterId(lEmitterIdValue, pEmitter);

        // Save the reference of the emitter. A weak reference is used to avoid memory leaks.
        TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
        TaskEmitterRef lEmitterRef = mEmittersById.get(lEmitterId);
        if (lEmitterRef == null) {
            lEmitterRef = new TaskEmitterRef(lEmitterId, pEmitter);
            mEmittersById.put(lEmitterId, lEmitterRef);
        } else {
            lEmitterRef.set(pEmitter);
        }

        // Try to terminate any task we can, which is possible if the new registered object is one of their emitter.
        for (TaskContainer<?> lContainer : mContainers) {
            if (lContainer.finish()) {
                notifyFinished(lContainer);
            } else {
                lContainer.restore(lEmitterId);
            }
        }
    }

    @Override
    public void unmanage(Object pEmitter) {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();

        // Remove an existing task emitter. If the emitter reference (in Java terms) is different from the object to remove, then
        // don't do anything. This could occur if a new object is managed before an older one with the same Id is unmanaged.
        // Typically, this could occur for example if an Activity X starts and then navigates to an Activity B which is,
        // according to Android lifecycle, started before A is stopped (the two activities are alive at the same time during a
        // short period of time).
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        if (lEmitterIdValue != null) {
            TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
            TaskEmitterRef lEmitterRef = mEmittersById.get(lEmitterId);
            if ((lEmitterRef != null) && (lEmitterRef.get() == pEmitter)) {
                lEmitterRef.clear();
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
    protected TaskEmitterRef resolveRef(Object pEmitter) {
        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. Note that an emitter Id can be null if no
        // dereferencing should be performed.
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        // Emitter id must not be the emitter itself or we have a leak. Warn user about this (tempting) configuration misuse.
        // Note that when we arrive here, pEmitter cannot be null.
        if (lEmitterIdValue == pEmitter) throw invalidEmitterId(lEmitterIdValue, pEmitter);

        WeakReference<Object> lEmitterRefValue = new WeakReference<Object>(pEmitter);
        // An unmanaged object doesn't have any Id defined in the configuration, so create a unique one. For unmanaged objects,
        // which are unique "by reference", we use the WeakReference itself since it doesn't override Object.equals() (i.e. it is
        // unique "by reference" too). This is an optimization that could be perfectly replaced by something like "new Object()".
        TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), (lEmitterIdValue != null) ? lEmitterIdValue
                        : lEmitterRefValue);
        TaskEmitterRef lEmitterRef = mEmittersById.get(lEmitterId);
        // If emitter is managed by the user explicitly and is properly registered in the emitter list, do nothing. User can
        // update reference himself through manage(Object).
        // If emitter is managed (i.e. emitter Id returned by configuration) but is not in the emitter list, then a call to
        // manage() is missing. Throw an exception to warn the user.
        // If emitter is not managed by the user explicitly but is already present in the emitter list because another task
        // has been executed by the same emitter, do nothing. Indeed unmanaged emitter are unique and never need to be updated.
        // If emitter is not managed by the user explicitly but is not present in the emitter list, then start managing it so
        // that we will be able to restore its reference later (if it hasn't been garbage collected in-between).
        // Managed emitter.
        if (lEmitterRef != null) {
            return lEmitterRef;
        } else {
            // Managed emitter with missing call to manage().
            if (lEmitterIdValue != null) {
                throw emitterNotManaged(lEmitterIdValue, pEmitter);
            }
            // Unmanaged emitter case.
            else {
                if (mConfig.allowUnmanagedEmitters()) {
                    return new TaskEmitterRef(lEmitterId, pEmitter);
                } else {
                    throw unmanagedEmittersNotAllowed(pEmitter);
                }
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
    // protected WeakReference<?> resolveEmitter(TaskEmitterId pEmitterId) {
    // return mEmittersById.get(pEmitterId);
    // }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask) {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        return execute(pTask, pTask, null);
    }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask, TaskResult<TResult> pTaskResult) {
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
            // Start the (empty) service to tell the system that TaskManager is running and application shouldn't be killed if
            // possible until all enqueued tasks are running. This is just a suggestion of course...
            if ((mServiceIntent != null) && (mApplication.startService(mServiceIntent) == null)) {
                throw serviceNotDeclaredInManifest();
            }

            // Prepare the task (i.e. cache needed values) before adding it because the first operation can fail (and we don't
            // want to leave the container list in an incorrect state).
            TaskRef<TResult> lTaskRef = lContainer.prepareToRun(false);
            mContainers.add(lContainer);
            mConfig.resolveExecutor(pTask).execute(lContainer);
            return lTaskRef;
        } else {
            return null;
        }
    }

    @Override
    public <TResult> boolean rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult) {
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
    public void notifyProgress(final TaskProgress pProgress) {
        throw notCalledFromTask();
    }

    /**
     * Called when task is processed and finished to clean remaining references.
     * 
     * @param pContainer Finished task container.
     */
    protected void notifyFinished(final TaskContainer<?> pContainer) {
        mContainers.remove(pContainer);
        // All enqueued tasks are over. We can stop the (empty) service to tell Android nothing more is running.
        if ((mServiceIntent != null) && mContainers.isEmpty()) {
            mApplication.stopService(mServiceIntent);
        }
    }

    /**
     * Contains all the information about the task to execute: its Id, its handlers, a few cached values and the result or
     * exception... Most of the task execution logic is implemented here (referencing, dereferencing, etc.).
     * 
     * Note that TaskManager is implemented here because when a new task is executed from one other, then we need to keep track of
     * the context in which it is executed to remove or restore references properly. Thus, TaskManager methods called here are
     * just forwarded to the real TaskManager but with contextual information.
     */
    private class TaskContainer<TResult> implements Runnable, TaskManager {
        // Handlers
        private Task<TResult> mTask;
        private TaskResult<TResult> mTaskResult;

        // Container info.
        private TaskRef<TResult> mTaskRef;
        private TaskId mTaskId;
        private TaskManagerConfig mConfig;
        private TaskContainer<?> mParentContainer;
        private List<TaskEmitterDescriptor> mEmitterDescriptors;
        private int mReferenceCounter;

        // Task result and state.
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mProcessed;
        private boolean mFinished;

        // Cached values.
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
            mEmitterDescriptors = new ArrayList<TaskEmitterDescriptor>(1); // Most of the time, a task will have only one emitter.
            mReferenceCounter = 0;

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;

            mProgressRunnable = null;
        }

        /**
         * Initialize the container (i.e. cache needed values, ...) before running it.
         */
        protected TaskRef<TResult> prepareToRun(boolean pIsRestored) {
            prepareReferenceCounter();

            mEmitterDescriptors.clear();
            // TODO Reference / Dereference...
            if (mTaskResult instanceof TaskStart) {
                ((TaskStart) mTaskResult).onStart(pIsRestored);
            }

            if (mTask != mTaskResult) {
                prepareTask();
            }
            prepareTaskResult();

            dereferenceEmitter();
            return mTaskRef;
        }

        private void prepareReferenceCounter() {
            TaskContainer<?> lParentContainer = mParentContainer;
            while (lParentContainer != null) {
                ++lParentContainer.mReferenceCounter;
                lParentContainer = lParentContainer.mParentContainer;
            }
            ++mReferenceCounter;
        }

        /**
         * Dereference the task itself it is disjoint from its handlers. This is definitive.
         */
        private void prepareTask() {
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
        private void prepareTaskResult() {
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
        private void prepareField(Field pField) {
            try {
                pField.setAccessible(true);

                // Extract the emitter "reflectively" and compute its Id.
                TaskEmitterRef lEmitterRef = null;
                Object lEmitter = pField.get(mTaskResult);
                if (lEmitter != null) {
                    lEmitterRef = resolveRef(lEmitter);
                }
                // If reference is null, that means the emitter is probably used in a parent container and already managed.
                // Try to find its Id in parent containers.
                else {
                    lEmitterRef = findEmitterRefInParentContainers(pField);
                }

                if (lEmitterRef != null) {
                    mEmitterDescriptors.add(new TaskEmitterDescriptor(pField, lEmitterRef));
                } else {
                    throw emitterIdCouldNotBeDetermined(mTaskResult);
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        private TaskEmitterRef findEmitterRefInParentContainers(Field pField) {
            TaskContainer<?> lParentContainer = mParentContainer;
            while (lParentContainer != null) {
                TaskEmitterRef lEmitterRef;
                for (TaskEmitterDescriptor lParentEmitterDescriptor : lParentContainer.mEmitterDescriptors) {
                    lEmitterRef = lParentEmitterDescriptor.findSameRef(pField);
                    if (lEmitterRef != null) {
                        return lEmitterRef;
                    }
                }
                lParentContainer = lParentContainer.mParentContainer;
            }
            return null;
        }

        /**
         * Dereference the emitter (which is an outer object) from the task (which is an inner class) and return its Id. Emitter
         * references are stored internally so that they can be restored later when the task has finished its computation. Note
         * that an emitter Id can be null if a task is not an inner class or if no dereferencing should be applied.
         * 
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        private void dereferenceEmitter() {
            if (mParentContainer != null) mParentContainer.dereferenceEmitter();

            if ((--mReferenceCounter) == 0) {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    lEmitterDescriptor.dereference(mTaskResult);
                }
            }
        }

        /**
         * Restore the emitter back into the task.
         * 
         * @param pContainer Container that contains the task to restore.
         * @return True if restoration could be performed properly. This may be false if a previously managed object become
         *         unmanaged meanwhile.
         */
        private boolean referenceEmitter() {
            // Try to restore emitters in parent containers.
            boolean lRestored = (mParentContainer == null) || mParentContainer.referenceEmitter();

            // Restore references for current container.
            if ((mReferenceCounter++) == 0) {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    lRestored &= lEmitterDescriptor.reference(mTaskResult);
                }
            }
            return lRestored;
        }

        /**
         * Run background task on Executor-thread
         */
        public void run() {
            try {
                mResult = mTask.onProcess(this);
            } catch (final Exception eException) {
                mThrowable = eException;
            } finally {
                mUIQueue.post(new Runnable() {
                    public void run() {
                        mProcessed = true;
                        if (finish()) {
                            notifyFinished(TaskContainer.this);
                        }
                    }
                });
            }
        }

        /**
         * TODO Comments.
         * 
         * @param pTaskEmitterId
         */
        protected void restore(TaskEmitterId pTaskEmitterId) {
            // if (mTaskResult instanceof TaskStart) {
            // for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
            // if (lEmitterDescriptor.mEmitterId.equals(pTaskEmitterId)) {
            // try {
            // if (referenceEmitter()) {
            // ((TaskStart) mTaskResult).onStart(true);
            // }
            // }
            // // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
            // catch (RuntimeException eRuntimeException) {
            // if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
            // } finally {
            // dereferenceContainer(TaskContainer.this);
            // }
            // return;
            // }
            // }
            // }
        }

        /**
         * Replace the previous task handler (usually implemented in the task itself) with a new one. Previous handler is lost.
         * 
         * @param pTaskResult Task handler that must replace previous one.
         * @param pParentContainer Context in which the task handler is executed.
         */
        protected void switchHandler(TaskResult<TResult> pTaskResult, TaskContainer<?> pParentContainer) {
            mTaskResult = pTaskResult;
            mParentContainer = pParentContainer;
            mProgressRunnable = null;
            prepareToRun(true);
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
        protected boolean finish() {
            // Execute task termination handlers if they have not been yet (but only if the task has been fully processed).
            if (!mProcessed || mFinished) return false;
            // Try to restore the emitter reference. If we can't, ask the configuration what to do.
            if (!referenceEmitter() && mConfig.keepResultOnHold(mTask)) {
                // Rollback any modification to leave container in a clean state.
                dereferenceEmitter();
                return false;
            } else {
                try {
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
                    dereferenceEmitter();
                    mFinished = true;
                }
                return true;
            }
        }

        @Override
        public void manage(Object pEmitter) {
            TaskManagerAndroid.this.manage(pEmitter);
        }

        @Override
        public void unmanage(Object pEmitter) {
            TaskManagerAndroid.this.unmanage(pEmitter);
        }

        @Override
        public <TOtherResult> TaskRef<TOtherResult> execute(Task<TOtherResult> pTask) {
            return TaskManagerAndroid.this.execute(pTask, pTask, this);
        }

        @Override
        public <TOtherResult> TaskRef<TOtherResult> execute(Task<TOtherResult> pTask, TaskResult<TOtherResult> pTaskResult) {
            return TaskManagerAndroid.this.execute(pTask, pTaskResult, this);
        }

        @Override
        public <TOtherResult> boolean rebind(TaskRef<TOtherResult> pTaskRef, TaskResult<TOtherResult> pTaskResult) {
            return TaskManagerAndroid.this.rebind(pTaskRef, pTaskResult, this);
        }

        @Override
        public void notifyProgress(final TaskProgress pTaskProgress) {
            if (pTaskProgress == null) throw new NullPointerException("Progress is null");

            Runnable lProgressRunnable;
            // @violations off: Optimization to avoid allocating a runnable each time progress is handled from the task itself.
            if ((pTaskProgress == mTaskResult) && (mProgressRunnable != null)) { // @violations on
                lProgressRunnable = mProgressRunnable;
            }
            // Create a runnable to handle task progress and reference/dereference properly the task before/after task execution.
            else {
                lProgressRunnable = new Runnable() {
                    public void run() {
                        try {
                            if (referenceEmitter()) {
                                pTaskProgress.onProgress(TaskManagerAndroid.this);
                            }
                        }
                        // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
                        catch (RuntimeException eRuntimeException) {
                            if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                        } finally {
                            dereferenceEmitter();
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

        public boolean hasSameRef(TaskRef<?> pTaskRef) {
            return mTaskRef.equals(pTaskRef);
        }

        @Override
        public boolean equals(Object pOther) {
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
        public int hashCode() {
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
    private static final class TaskEmitterDescriptor {
        private final Field mEmitterField;
        private final TaskEmitterRef mEmitterRef;

        public TaskEmitterDescriptor(Field pEmitterField, TaskEmitterRef pEmitterRef) {
            mEmitterField = pEmitterField;
            mEmitterRef = pEmitterRef;
        }

        public TaskEmitterRef findSameRef(Field pField) {
            return (pField.getType() == mEmitterField.getType()) ? mEmitterRef : null;
        }

        public boolean reference(TaskResult<?> pTaskResult) {
            try {
                Object lEmitter = mEmitterRef.get();
                if (lEmitter != null) {
                    mEmitterField.set(pTaskResult, mEmitterRef.get());
                    return true;
                } else {
                    return false;
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        public void dereference(TaskResult<?> pTaskResult) {
            try {
                mEmitterField.set(pTaskResult, null);
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        @Override
        public String toString() {
            return "TaskEmitterDescriptor [mEmitterField=" + mEmitterField + ", mEmitterRef=" + mEmitterRef + "]";
        }
    }

    /**
     * TODO
     */
    private static final class TaskEmitterRef {
        private final TaskEmitterId mEmitterId;
        private WeakReference<?> mEmitterRef;

        public TaskEmitterRef(TaskEmitterId pEmitterId, Object pEmitterValue) {
            mEmitterId = pEmitterId;
            set(pEmitterValue);
        }

        public Object get() {
            return (mEmitterRef != null) ? mEmitterRef.get() : null;
        }

        public void set(Object pEmitterValue) {
            mEmitterRef = new WeakReference<Object>(pEmitterValue);
        }

        public void clear() {
            mEmitterRef = null;
        }

        @Override
        public boolean equals(Object pOther) {
            if (this == pOther) return true;
            if (pOther == null) return false;
            if (getClass() != pOther.getClass()) return false;

            TaskEmitterRef lOther = (TaskEmitterRef) pOther;
            if (mEmitterId == null) return lOther.mEmitterId == null;
            else return mEmitterId.equals(lOther.mEmitterId);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mEmitterId == null) ? 0 : mEmitterId.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "TaskEmitter [mEmitterId=" + mEmitterId + ", mEmitterRef=" + mEmitterRef + "]";
        }
    }

    /**
     * Contains the information to store the Id of an emitter. Emitter class is necessary since if internal Ids may be quite
     * common and thus similar between emitters of different types (e.g. fragments which have integer Ids starting from 0).
     */
    private static final class TaskEmitterId {
        private final Class<?> mType;
        private final Object mId;

        public TaskEmitterId(Class<?> pType, Object pId) {
            super();
            mType = pType;
            mId = pId;
        }

        @Override
        public boolean equals(Object pOther) {
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
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mId == null) ? 0 : mId.hashCode());
            result = prime * result + ((mType == null) ? 0 : mType.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "TaskEmitterId [mType=" + mType + ", mId=" + mId + "]";
        }
    }
}