package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.emitterIdCouldNotBeDetermined;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.emitterNotManaged;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.innerTasksNotAllowed;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.internalError;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.invalidEmitterId;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.mustBeExecutedFromUIThread;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.notCalledFromTask;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.progressCalledAfterTaskFinished;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.taskExecutedFromUnexecutedTask;
import static com.codexperiments.robolabor.task.android.AndroidTaskManagerException.unmanagedEmittersNotAllowed;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Application;
import android.os.Looper;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskManagerConfig;
import com.codexperiments.robolabor.task.TaskRef;
import com.codexperiments.robolabor.task.TaskScheduler;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskHandler;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskNotifier;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;
import com.codexperiments.robolabor.task.handler.TaskStart;
import com.codexperiments.robolabor.task.id.TaskId;
import com.codexperiments.robolabor.task.util.AutoCleanMap;
import com.codexperiments.robolabor.task.util.EmptyLock;

/**
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
 * TODO pending(TaskType)
 */
public class AndroidTaskManager implements TaskManager {
    private static final int DEFAULT_CAPACITY = 64;
    // To generate task references.
    private static int TASK_REF_COUNTER;

    private TaskScheduler mDefaultScheduler;
    private LockingStrategy mLockingStrategy;
    private TaskManagerConfig mConfig;
    // All the current running tasks.
    private Set<TaskContainer<?, ?, ?>> mContainers;
    // Keep tracks of all emitters. Note that TaskEmitterRef uses a weak reference to avoid memory leaks. This Map is never
    // cleaned and accumulates references because it assumes that any object that managed object set doesn't grow infinitely but
    // is rather limited (e.g. typically all fragments, activity and manager in an Application).
    private Map<TaskEmitterId, TaskEmitterRef> mEmitters;
    // Allows getting back an existing descriptor through its handler when dealing with nested tasks. An AutoCleanMap is necessary
    // since there is no way to know when a handler are not necessary anymore.
    private Map<TaskHandler, TaskDescriptor<?, ?, ?>> mDescriptors;

    static {
        TASK_REF_COUNTER = Integer.MIN_VALUE;
    }

    public AndroidTaskManager(Application pApplication, TaskManagerConfig pConfig) {
        super();

        mDefaultScheduler = new AndroidUITaskScheduler();
        mConfig = pConfig;
        mLockingStrategy = new UIThreadLockingStrategy();
        mLockingStrategy.createManager(this);
        mContainers = Collections.newSetFromMap(new ConcurrentHashMap<TaskContainer<?, ?, ?>, Boolean>(DEFAULT_CAPACITY));
        mEmitters = new ConcurrentHashMap<TaskEmitterId, TaskEmitterRef>(DEFAULT_CAPACITY);
        mDescriptors = new AutoCleanMap<TaskHandler, TaskDescriptor<?, ?, ?>>(DEFAULT_CAPACITY);
    }

    @Override
    public void manage(Object pEmitter) {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");
        mLockingStrategy.checkCallIsAllowed();

        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration and can be null if emitter is not managed.
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        // Emitter id must not be the emitter itself or we have a leak. Warn user about this (tempting) configuration misuse.
        if ((lEmitterIdValue == null) || (lEmitterIdValue == pEmitter)) throw invalidEmitterId(lEmitterIdValue, pEmitter);

        // Save the reference of the emitter. Initialize it lazily if it doesn't exist.
        TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
        TaskEmitterRef lEmitterRef = mEmitters.get(lEmitterId);
        if (lEmitterRef == null) {
            lEmitterRef = mEmitters.put(lEmitterId, new TaskEmitterRef(lEmitterId, pEmitter));
        } else {
            lEmitterRef.set(pEmitter);
        }

        // Try to terminate any task we can, which is possible if the newly managed emitter is one of their emitter.
        // TODO Maybe we should add a reference from the TaskEmitterId to the containers to avoid this lookup.
        for (TaskContainer<?, ?, ?> lContainer : mContainers) {
            lContainer.manage(lEmitterId);
        }
    }

    @Override
    public void unmanage(Object pEmitter) {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");
        mLockingStrategy.checkCallIsAllowed();

        // Remove an existing task emitter. If the emitter reference (in Java terms) is different from the object to remove, then
        // don't do anything. This could occur if a new object is managed before an older one with the same Id is unmanaged.
        // Typically, this could occur for example if an Activity X starts and then navigates to an Activity B which is,
        // according to Android lifecycle, started before A is stopped (the two activities are alive at the same time during a
        // short period of time).
        // TODO (lEmitterRef.get() == pEmitter) is not a proper way to handle unmanage() when dealing with activities since this
        // can lead to concurrency defects. It would be better to force call to unmanage().
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        if (lEmitterIdValue != null) {
            TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
            TaskEmitterRef lEmitterRef = mEmitters.get(lEmitterId);
            if ((lEmitterRef != null) && (lEmitterRef.get() == pEmitter)) {
                lEmitterRef.clear();
            }
        }
    }

    @Override
    public <TParam, TProgress, TResult> TaskRef<TResult> execute(Task<TParam, TProgress, TResult> pTask) {
        return execute(pTask, pTask);
    }

    public class TaskPipeBuilder<TResult> {
        private TaskContainer<?, ?, TResult> mInitialContainer;
        private TaskContainer<?, ?, ?> mCurrentContainer;

        public TaskPipeBuilder(Task<?, ?, TResult> pTask, TaskResult<TResult> pTaskResult) {
            mInitialContainer = prepare(pTask, pTaskResult);
            mCurrentContainer = mInitialContainer;
        }

        public <TOtherResult> TaskPipeBuilder<TOtherResult> pipe(Task<TResult, ?, TOtherResult> pTask) {
            return pipe(pTask, pTask);
        }

        @SuppressWarnings("unchecked")
        public <TOtherProgress, TOtherResult> TaskPipeBuilder<TOtherResult> pipe(Task<TResult, ?, TOtherResult> pTask,
                                                                                 TaskResult<TOtherResult> pTaskResult)
        {
            TaskContainer<?, ?, ?> lNewContainer = prepare(pTask, pTaskResult);
            mCurrentContainer.mPipeContainer = lNewContainer;
            mCurrentContainer = lNewContainer;
            return (TaskPipeBuilder<TOtherResult>) this;
        }

        public TaskRef<TResult> execute() {
            gogogo(mInitialContainer);
            return mInitialContainer.mTaskRef;
        }
    }

    @Override
    public <TResult> TaskPipeBuilder<TResult> when(Task<?, ?, TResult> pTask) {
        return new TaskPipeBuilder<TResult>(pTask, pTask);
    }

    @Override
    public <TResult> TaskPipeBuilder<TResult> when(Task<?, ?, TResult> pTask, TaskResult<TResult> pTaskResult) {
        return new TaskPipeBuilder<TResult>(pTask, pTaskResult);
    }

    public <TParam, TProgress, TResult> TaskContainer<TParam, TProgress, TResult> prepare(Task<TParam, TProgress, TResult> pTask,
                                                                                          TaskResult<TResult> pTaskResult)
    {
        if (pTask == null) throw new NullPointerException("Task is null");
        if (pTaskResult == null) throw new NullPointerException("TaskResult is null");
        mLockingStrategy.checkCallIsAllowed();

        // Create a container to run the task.
        TaskContainer<TParam, TProgress, TResult> lContainer = new TaskContainer<TParam, TProgress, TResult>(pTask,
                                                                                                             mDefaultScheduler);
        // Save the task before running it.
        // Note that it is safe to add the task to the container since it is an empty stub that shouldn't create any side-effect.
        if (mContainers.add(lContainer)) {
            // Prepare the task (i.e. initialize and cache needed values) after adding it because prepareToRun() is a bit
            // expensive and should be performed only if necessary.
            try {
                lContainer.prepareToRun(pTaskResult);
                return lContainer;
            }
            // If preparation operation fails, try to leave the manager in a consistent state without memory leaks.
            catch (RuntimeException eRuntimeException) {
                mContainers.remove(lContainer);
                throw eRuntimeException;
            }
        }
        // If an identical task is already executing, do nothing.
        else {
            return null;
        }
    }

    public void gogogo(TaskContainer<?, ?, ?> pContainer) {
        if (pContainer != null) {
            mConfig.resolveExecutor(pContainer.mTask).execute(pContainer);
        }
    }

    @Override
    public <TParam, TProgress, TResult> TaskRef<TResult> execute(Task<TParam, TProgress, TResult> pTask,
                                                                 TaskResult<TResult> pTaskResult)
    {
        if (pTask == null) throw new NullPointerException("Task is null");
        if (pTaskResult == null) throw new NullPointerException("TaskResult is null");
        mLockingStrategy.checkCallIsAllowed();

        // Create a container to run the task.
        TaskContainer<TParam, TProgress, TResult> lContainer = new TaskContainer<TParam, TProgress, TResult>(pTask,
                                                                                                             mDefaultScheduler);
        // Save the task before running it.
        // Note that it is safe to add the task to the container since it is an empty stub that shouldn't create any side-effect.
        if (mContainers.add(lContainer)) {
            // Prepare the task (i.e. initialize and cache needed values) after adding it because prepareToRun() is a bit
            // expensive and should be performed only if necessary.
            try {
                TaskRef<TResult> lTaskRef = lContainer.prepareToRun(pTaskResult);
                mConfig.resolveExecutor(pTask).execute(lContainer);
                return lTaskRef;
            }
            // If preparation operation fails, try to leave the manager in a consistent state without memory leaks.
            catch (RuntimeException eRuntimeException) {
                mContainers.remove(lContainer);
                throw eRuntimeException;
            }
        }
        // If an identical task is already executing, do nothing.
        else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <TParam, TProgress, TResult> boolean rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult) {
        if (pTaskRef == null) throw new NullPointerException("Task is null");
        if (pTaskResult == null) throw new NullPointerException("TaskResult is null");
        mLockingStrategy.checkCallIsAllowed();

        // TODO It seems possible to add a reference from the TaskRef to the TaskContainer to avoid this lookup.
        for (TaskContainer<?, ?, ?> lContainer : mContainers) {
            // Cast safety is guaranteed by the execute() method that returns a properly typed TaskRef for a new container.
            ((TaskContainer<TParam, TProgress, TResult>) lContainer).rebind(pTaskRef, pTaskResult);
            return true;
        }
        return false;
    }

    @Override
    public void notifyProgress() {
        throw notCalledFromTask();
    }

    /**
     * Called internally when initializing a TaskDescriptor to a reference to an emitter, either managed or not. If the emitter is
     * not managed, then return an unmanaged reference (i.e. that is not stored in mEmitters).
     * 
     * @param pEmitter Emitter to find the reference of.
     * @return Emitter reference. No null is returned.
     */
    protected TaskEmitterRef resolveRef(Object pEmitter) {
        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. Note that an emitter Id can be null if no
        // dereferencing should be performed.
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        // Emitter id must not be the emitter itself or we have a leak. Warn user about this (tempting) configuration misuse.
        // Note that when we arrive here, pEmitter can't be null.
        if (lEmitterIdValue == pEmitter) throw invalidEmitterId(lEmitterIdValue, pEmitter);

        TaskEmitterRef lEmitterRef;
        // Managed emitter case.
        if (lEmitterIdValue != null) {
            TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
            lEmitterRef = mEmitters.get(lEmitterId);
            // If emitter is managed by the user explicitly and is properly registered in the emitter list, do nothing. User can
            // update reference himself through manage(Object) later. But if emitter is managed (i.e. emitter Id returned by
            // configuration is not null) but is not in the emitter list, then a call to manage() is missing. Warn the user.
            if (lEmitterRef == null) throw emitterNotManaged(lEmitterIdValue, pEmitter);
        }
        // Unmanaged emitter case.
        else {
            if (!mConfig.allowUnmanagedEmitters()) throw unmanagedEmittersNotAllowed(pEmitter);
            // TODO This is wrong! There should be only one TaskEmitterRef per emitter or concurrency problems may occur.
            lEmitterRef = new TaskEmitterRef(pEmitter);
        }
        return lEmitterRef;
    }

    /**
     * Called when task is processed and finished to clean remaining references.
     * 
     * @param pContainer Finished task container.
     */
    protected void notifyFinished(final TaskContainer<?, ?, ?> pContainer) {
        mContainers.remove(pContainer);
    }

    /**
     * Wrapper class that contains all the information about the task to execute.
     */
    private class TaskContainer<TParam, TProgress, TResult> implements Runnable, TaskNotifier<TProgress> {
        // Handlers
        private Task<TParam, TProgress, TResult> mTask;

        // Container info.
        private volatile TaskDescriptor<TParam, TProgress, TResult> mDescriptor;
        private final TaskRef<TResult> mTaskRef;
        private final TaskId mTaskId;
        private final TaskScheduler mScheduler;

        // Task result and state.
        private TParam mParam;
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mRunning;
        private boolean mFinished;

        private TaskContainer<?, ?, ?> mPipeContainer;

        public TaskContainer(Task<TParam, TProgress, TResult> pTask, TaskScheduler pScheduler) {
            super();
            mTask = pTask;

            mDescriptor = null;
            mTaskRef = new TaskRef<TResult>(TASK_REF_COUNTER++);
            mTaskId = (pTask instanceof TaskIdentifiable) ? ((TaskIdentifiable) pTask).getId() : null;
            mScheduler = pScheduler;

            mResult = null;
            mThrowable = null;
            mRunning = true;
            mFinished = false;
        }

        /**
         * Initialize the container before running it.
         */
        protected TaskRef<TResult> prepareToRun(TaskResult<TResult> pTaskResult) {
            // Initialize the descriptor safely in its corner and dereference required values.
            final TaskDescriptor<TParam, TProgress, TResult> lDescriptor = new TaskDescriptor<TParam, TProgress, TResult>(pTaskResult);
            if (!lDescriptor.needDereferencing(mTask)) {
                prepareTask();
            }
            // Make the descriptor visible once fully initialized.
            mDescriptor = lDescriptor;
            // Execute onStart() handler.
            mScheduler.scheduleIfNecessary(new Runnable() {
                public void run() {
                    lDescriptor.onStart(true);
                }
            });

            // Save the descriptor so that any child task can use current descriptor as a parent.
            mDescriptors.put(pTaskResult, lDescriptor); // TODO Global lock that could lead to contention. Check for optim.
            return mTaskRef;
        }

        /**
         * Dereference the task itself if it is disjoint from its handlers. This is definitive. No code inside the task is allowed
         * to access this$x references.
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
         * Run background task on Executor-thread
         */
        public void run() {
            try {
                mResult = mTask.onProcess(mParam, this);
            } catch (final Exception eException) {
                mThrowable = eException;
            } finally {
                mScheduler.schedule(new Runnable() {
                    public void run() {
                        mRunning = false;
                        finish();
                    }
                });
            }
        }

        /**
         * If a newly managed emitter is used by the present container, then call onStart() handler. Note this code allows
         * onStart() to be called even if task is rebound in-between. Thus, if a manager is restored with an emitter A and then
         * task is rebound with a new emitter B from another thread, it is possible to have onStart() called on both A and B in
         * any order. This might not be what you expect but honestly mixing rebind() and manage() in different threads is not a
         * very good practice anyway... The only real guarantee given is that onStart() will never be called after onFinish().
         * 
         * @param pEmitterId Id of the newly managed emitter. If not used by the container, nothing is done.
         */
        public void manage(TaskEmitterId pEmitterId) {
            final TaskDescriptor<TParam, TProgress, TResult> lDescriptor = mDescriptor;
            // Note that descriptor can be null if container has been added to the global list but hasn't been prepared yet.
            if ((lDescriptor != null) && lDescriptor.usesEmitter(pEmitterId)) {
                restore(lDescriptor);
            }
        }

        /**
         * Replace the previous task handler with a new one. Previous handler is lost. See manage() for concurrency concerns.
         * 
         * @param pTaskRef Reference of the task to rebind to. If different, nothing is performed.
         * @param pTaskResult Task handler that must replace previous one.
         */
        public void rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult) {
            if (mTaskRef.equals(pTaskRef)) {
                final TaskDescriptor<TParam, TProgress, TResult> lDescriptor = new TaskDescriptor<TParam, TProgress, TResult>(pTaskResult);
                mDescriptor = lDescriptor;
                restore(lDescriptor);
                // Save the descriptor so that any child task can use current descriptor as a parent.
                mDescriptors.put(pTaskResult, lDescriptor); // TODO Global lock that could lead to contention. Check for optim.
            }
        }

        /**
         * Calls onStart() handler when a task is rebound to another object or when an emitter gets managed again.
         * 
         * @param pDescriptor Descriptor to use to call onStart(). Necessary to use this parameter as descriptor can be changed
         *            concurrently through rebind.
         */
        private void restore(final TaskDescriptor<TParam, TProgress, TResult> pDescriptor) {
            mScheduler.scheduleIfNecessary(new Runnable() {
                public void run() {
                    if (!finish()) {
                        pDescriptor.onStart(true);
                    }
                }
            });
        }

        /**
         * Try to execute task termination handlers (i.e. onFinish and onFail). The latter may not be executed if at least one of
         * the outer object reference can't be restored. When the task is effectively finished, the corresponding flag is set to
         * prevent any other invocation. That way, finish() can be called at any time:
         * <ul>
         * <li>When task isn't finished yet, in which case nothing happens. This can occur if a new instance of the emitter
         * becomes managed while task is still executing: the task manager try to call finish on all tasks.</li>
         * <li>When task has just finished, i.e. finish is called from the computation thread, and its emitter is available. In
         * this case, the flag is set to true and task termination handler is triggered.</li>
         * <li>When task has just finished but its emitter is not available yet, i.e. it has been unmanaged. In this case, the
         * flag is set to true but task termination handler is NOT triggered. it will be triggered later when a new emitter (with
         * the same Id) becomes managed.</li>
         * <li>When an emitter with the same Id as the previously managed-then-unmanaged one becomes managed. In this case the
         * flag is already true but task termination handler may have not been called yet (i.e. if mFinished is false). This can
         * be done now. Note hat it is possible to have finish() called several times since there may be a delay between finish()
         * call and execution as it is posted on the UI Thread.</li>
         * </ul>
         * 
         * @return True if the task could be finished and its termination handlers executed or false otherwise.
         */
        private boolean finish() {
            // Execute task termination handlers if they have not been yet (but only if the task has been fully processed).
            if (mRunning) return false;
            if (mFinished) return true;

            TaskDescriptor<TParam, TProgress, TResult> lDescriptor = mDescriptor;
            // TODO Don't like the configuration parameter here.
            mFinished = lDescriptor.onFinish(mResult, mThrowable, mConfig.keepResultOnHold(mTask));
            if (mFinished) {
                notifyFinished(this);
                gogogo(mPipeContainer);
            }
            return mFinished;
        }

        @Override
        public void notifyProgress(final TProgress pProgress) {
            // Progress is always executed on the scheduler Thread but sent from the background Thread.
            if (!mRunning) throw progressCalledAfterTaskFinished();
            // TODO Cache.
            mScheduler.schedule(new Runnable() {
                public void run() {
                    mDescriptor.onProgress(pProgress);
                }
            });
        }

        @Override
        public boolean equals(Object pOther) {
            if (this == pOther) return true;
            if (pOther == null) return false;
            if (getClass() != pOther.getClass()) return false;

            TaskContainer<?, ?, ?> lOtherContainer = (TaskContainer<?, ?, ?>) pOther;
            // Check equality on the user-defined task Id if possible.
            if (mTaskId != null) {
                return mTaskId.equals(lOtherContainer.mTaskId);
            }
            // If the task has no Id, then we use task equality method. This is likely to turn into a simple reference check.
            else if (mTask != null) {
                return mTask.equals(lOtherContainer.mTask);
            }
            // A container can't be created with a null task. So the following case should never occur.
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
     * Contains all the information necessary to restore all the emitters (even parent emitters) of a task. Once prepareToRun() is
     * called, the content of this class is not modified anymore (except the emitter and the reference counter dedicated to
     * referencing and dereferencing).
     */
    private class TaskDescriptor<TParam, TProgress, TResult> {
        private final TaskResult<TResult> mTaskResult;
        private List<TaskEmitterDescriptor> mEmitterDescriptors; // Never modified once initialized in prepareDescriptor().
        private List<TaskDescriptor<?, ?, ?>> mParentDescriptors; // Never modified once initialized in prepareDescriptor().
        // Counts the number of time a task has been referenced without being dereferenced. A task will be dereferenced only when
        // this counter reaches 0, which means that no other task needs references to be set. This situation can occur for example
        // when starting a child task from a parent task handler (e.g. in onFinish()): when the child task is launched, it must
        // not dereference emitters because the parent task is still in its onFinish() handler and may need references to them.
        private int mReferenceCounter;
        private final Lock mLock;

        // TODO Boolean option to indicate if we should look for emitter or if task is not "managed".
        public TaskDescriptor(TaskResult<TResult> pTaskResult) {
            mTaskResult = pTaskResult;
            mEmitterDescriptors = null;
            mParentDescriptors = null;
            mReferenceCounter = 0;
            mLock = mLockingStrategy.createLock();

            prepareDescriptor();
        }

        public boolean needDereferencing(Task<TParam, TProgress, TResult> pTask) {
            return pTask == mTaskResult;
        }

        public boolean usesEmitter(TaskEmitterId pEmitterId) {
            if (mEmitterDescriptors != null) {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    if (lEmitterDescriptor.usesEmitter(pEmitterId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Locate all the outer object references (e.g. this$0) inside the task class, manage them if necessary and cache emitter
         * field properties for later use. Check is performed recursively on all super classes too.
         */
        private void prepareDescriptor() {
            try {
                // Go through the main class and each of its super classes and look for "this$" fields.
                Class<?> lTaskResultClass = mTaskResult.getClass();
                while (lTaskResultClass != Object.class) {
                    // If current class is an inner class...
                    if ((lTaskResultClass.getEnclosingClass() != null) && !Modifier.isStatic(lTaskResultClass.getModifiers())) {
                        // Find emitter references and generate a descriptor from them.
                        for (Field lField : lTaskResultClass.getDeclaredFields()) {
                            if (lField.getName().startsWith("this$")) {
                                prepareEmitterField(lField);
                                // There should be only one outer reference per "class" in the Task class hierarchy. So we can
                                // stop as soon as the field is found as there won't be another.
                                break;
                            }
                        }
                    }
                    lTaskResultClass = lTaskResultClass.getSuperclass();
                }
            } finally {
                if (mEmitterDescriptors != null) {
                    for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                        lEmitterDescriptor.dereference(mTaskResult);
                    }
                }
            }
        }

        /**
         * Find and save the descriptor of the corresponding field, i.e. an indirect (weak) reference pointing to the emitter
         * through its Id or a simple indirect (weak) reference for unmanaged emitters.
         * 
         * @param pField Field to manage.
         */
        private void prepareEmitterField(Field pField) {
            try {
                pField.setAccessible(true);

                // Extract the emitter "reflectively" and compute its Id.
                TaskEmitterRef lEmitterRef = null;
                Object lEmitter = pField.get(mTaskResult);

                if (lEmitter != null) {
                    lEmitterRef = resolveRef(lEmitter);
                    lookForParentDescriptor(pField, lEmitter);
                }
                // If reference is null, that means the emitter is probably used in a parent container and already managed.
                // Try to find its Id in parent containers.
                else {
                    // Not sure there is a problem here. The list of parent descriptors should be entirely created before to be
                    // sure we can properly resolve reference. However this$x fields are processed from child class to super
                    // classes. My guess is that top-most child class will always have its outer reference filled, which itself
                    // will point to parent outer objects. And if one of the outer reference is created explicitly through a
                    // "myOuter.new Inner()", well the outer class reference myOuter cannot be null or a NullPointerException is
                    // thrown by the Java language anyway. But that remains late-night suppositions... Anyway if it doesn't work
                    // it probably means you're just writing really bad code so just stop it please! Note that this whole case can
                    // occur only when onFinish() is called with keepResultOnHold option set to false (in which case referencing
                    // is not guaranteed be fully applied).
                    lEmitterRef = resolveRefInParentDescriptors(pField);
                }

                if (lEmitterRef != null) {
                    if (mEmitterDescriptors == null) {
                        // Most of the time, a task will have only one emitter. Hence a capacity of 1.
                        mEmitterDescriptors = new ArrayList<TaskEmitterDescriptor>(1);
                    }
                    mEmitterDescriptors.add(new TaskEmitterDescriptor(pField, lEmitterRef));
                } else {
                    // Maybe this is too brutal and we should do nothing, hoping that no access will be made. But for the moment I
                    // really think this case should never happen under normal conditions. See the big paragraph above...
                    throw emitterIdCouldNotBeDetermined(mTaskResult);
                }
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            }
        }

        /**
         * Sometimes, we cannot resolve a parent emitter reference because it has already been dereferenced. In that case, we
         * should find the emitter reference somewhere in parent descriptors.
         * 
         * @param pField Emitter field.
         * @return
         */
        private TaskEmitterRef resolveRefInParentDescriptors(Field pField) {
            if (mParentDescriptors != null) {
                for (TaskDescriptor<?, ?, ?> lParentDescriptor : mParentDescriptors) {
                    TaskEmitterRef lEmitterRef;
                    if (mEmitterDescriptors != null) {
                        for (TaskEmitterDescriptor lParentEmitterDescriptor : lParentDescriptor.mEmitterDescriptors) {
                            // We have found the right ref if its field has the same type than the field of the emitter we look
                            // for.
                            // I turned my mind upside-down but this seems to work.
                            lEmitterRef = lParentEmitterDescriptor.hasSameType(pField);
                            if (lEmitterRef != null) return lEmitterRef;
                        }
                    }

                    lEmitterRef = lParentDescriptor.resolveRefInParentDescriptors(pField);
                    if (lEmitterRef != null) return lEmitterRef;
                }
            }
            return null;
        }

        /**
         * Check for parent tasks (i.e. a task containing directly or indirectly innertasks) and their descriptors that will be
         * necessary to restore absolutely all emitters of a task.
         * 
         * @param pField Emitter field.
         * @param pEmitter Effective emitter reference. Must not be null.
         */
        private void lookForParentDescriptor(Field pField, Object pEmitter) {
            if (TaskHandler.class.isAssignableFrom(pField.getType())) {
                TaskDescriptor<?, ?, ?> lDescriptor = mDescriptors.get(pEmitter);
                if (lDescriptor == null) throw taskExecutedFromUnexecutedTask(pEmitter);

                if (mParentDescriptors == null) {
                    // A task will have most of the time no parents. Hence lazy-initialization. But if that's not the case, then a
                    // task will usually have only one parent, rarely more. Hence a capacity of 1.
                    mParentDescriptors = new ArrayList<AndroidTaskManager.TaskDescriptor<?, ?, ?>>(1);
                }
                mParentDescriptors.add(lDescriptor);
            } else {
                try {
                    // Go through the main class and each of its super classes and look for "this$" fields.
                    Class<?> lTaskResultClass = pEmitter.getClass();
                    while (lTaskResultClass != Object.class) {
                        // If current class is an inner class...
                        if ((lTaskResultClass.getEnclosingClass() != null) && !Modifier.isStatic(lTaskResultClass.getModifiers())) {
                            // Find all parent emitter references and their corresponding descriptors.
                            for (Field lField : lTaskResultClass.getDeclaredFields()) {
                                if (lField.getName().startsWith("this$")) {
                                    lField.setAccessible(true);
                                    Object lParentEmitter = lField.get(pEmitter);
                                    if (lParentEmitter != null) {
                                        lookForParentDescriptor(lField, lParentEmitter);
                                    } else {
                                        // Look for the big comment in prepareEmitterField(). Here we try to check the whole
                                        // hierarchy of parent this$x to look for parent descriptors (not only this$x for the
                                        // handler class and its super classes). In this case, if we get a null, I really think we
                                        // are stuck if there is a Task handler and its associated descriptor hidden deeper behind
                                        // this null reference. Basically we can do nothing against this except maybe a warning as
                                        // code may still be correct if the null reference just hides e.g. a managed object (e.g.
                                        // an Activity). That's why an exception would be too brutal. User will get a
                                        // NullPointerException anyway if he try to go through such a reference. Again note that
                                        // this whole case can occur only when onFinish() is called with keepResultOnHold option
                                        // set to false (in which case referencing is not guaranteed be fully applied).
                                    }
                                    // There should be only one outer reference per "class" in the Task class hierarchy. So we can
                                    // stop as soon as the field is found as there won't be another.
                                    break;
                                }
                            }
                        }
                        lTaskResultClass = lTaskResultClass.getSuperclass();
                    }
                } catch (IllegalArgumentException eIllegalArgumentException) {
                    throw internalError(eIllegalArgumentException);
                } catch (IllegalAccessException eIllegalAccessException) {
                    throw internalError(eIllegalAccessException);
                }
            }
        }

        /**
         * Restore all the emitters back into the task handler. Called before each task handler is executed to avoid
         * NullPointerException when accessing outer emitters. Referencing can fail if an emitter has been unmanaged. In that
         * case, any set reference is rolled-back and dereferenceEmitter() shouldn't be called. But if referencing succeeds, then
         * dereferenceEmitter() MUST be called eventually (preferably using a finally block).
         * 
         * @param pRollbackOnFailure True to cancel referencing if one of the emitter cannot be restored, or false if partial
         *            referencing is allowed.
         * @return True if restoration was performed properly. This may be false if a previously managed object become unmanaged
         *         meanwhile.
         */
        private boolean referenceEmitter(boolean pRollbackOnFailure) {
            // Try to restore emitters in parent containers first. Everything is rolled-back if referencing fails.
            if (mParentDescriptors != null) {
                for (TaskDescriptor<?, ?, ?> lParentDescriptor : mParentDescriptors) {
                    if (!lParentDescriptor.referenceEmitter(pRollbackOnFailure)) return false;
                }
            }

            // Restore references for current container if referencing succeeded previously.
            if (mEmitterDescriptors != null) {
                mLock.lock();
                try {
                    // TODO There is a race problem in this code. A TaskEmitterRef can be used several times for one
                    // TaskDescriptor because of parent or superclass emitters ref that may be identical. In that case, a call
                    // to manage() on another thread during referenceEmitter() may cause two different emitters to be restored
                    // whereas we would expect the same ref.
                    if ((mReferenceCounter++) == 0) {
                        for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                            if (!lEmitterDescriptor.reference(mTaskResult) && pRollbackOnFailure) {
                                // Rollback modifications in case of failure.
                                --mReferenceCounter;
                                for (TaskEmitterDescriptor lRolledEmitterDescriptor : mEmitterDescriptors) {
                                    if (lRolledEmitterDescriptor == lEmitterDescriptor) break;
                                    lRolledEmitterDescriptor.dereference(mTaskResult);
                                }
                                return false;
                            }
                        }
                    }
                }
                // Note: Rollback any modifications if an exception occurs. Having an exception here denotes an internal bug.
                catch (AndroidTaskManagerException eTaskManagerAndroidException) {
                    --mReferenceCounter;
                    // Note that if referencing failed at some point, dereferencing is likely to fail too. That's not a big
                    // issue since an exception will be thrown in both cases anyway.
                    for (TaskEmitterDescriptor lRolledEmitterDescriptor : mEmitterDescriptors) {
                        lRolledEmitterDescriptor.dereference(mTaskResult);
                    }
                    throw eTaskManagerAndroidException;
                } finally {
                    mLock.unlock();
                }
            }
            return true;
        }

        /**
         * Remove emitter references from the task handler. Called after each task handler is executed to avoid memory leaks.
         * 
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        private void dereferenceEmitter() {
            // Try to dereference emitters in parent containers first.
            if (mParentDescriptors != null) {
                for (TaskDescriptor<?, ?, ?> lParentDescriptor : mParentDescriptors) {
                    lParentDescriptor.dereferenceEmitter();
                }
            }

            if (mEmitterDescriptors != null) {
                mLock.lock();
                try {
                    // Note: No need to rollback modifications if an exception occur. Leave references as is, thus creating a
                    // memory leak. We can't do much about it since having an exception here denotes an internal bug.
                    if ((--mReferenceCounter) == 0) {
                        for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                            lEmitterDescriptor.dereference(mTaskResult);
                        }
                    }
                } finally {
                    mLock.unlock();
                }
            }
        }

        public void onStart(boolean pIsRestored) {
            if (mTaskResult instanceof TaskStart) {
                if (referenceEmitter(true)) {
                    try {
                        ((TaskStart) mTaskResult).onStart(pIsRestored);
                    } catch (RuntimeException eRuntimeException) {
                        if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                    } finally {
                        dereferenceEmitter();
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        public void onProgress(TProgress pProgress) {
            if (mTaskResult instanceof TaskProgress) {
                if (referenceEmitter(true)) {
                    try {
                        ((TaskProgress<TProgress>) mTaskResult).onProgress(pProgress);
                    } catch (RuntimeException eRuntimeException) {
                        if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                    } finally {
                        dereferenceEmitter();
                    }
                }
            }
        }

        public boolean onFinish(TResult pResult, Throwable pThrowable, boolean pKeepResultOnHold) {
            // A task can be considered finished only if referencing succeed or if an option allows bypassing referencing failure.
            boolean lRestored = referenceEmitter(pKeepResultOnHold);
            if (!lRestored && pKeepResultOnHold) return false;

            // Run termination handlers.
            try {
                if (pThrowable == null) {
                    mTaskResult.onFinish(pResult);
                } else {
                    mTaskResult.onFail(pThrowable);
                }
            } catch (RuntimeException eRuntimeException) {
                if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
            } finally {
                // After task is over, it may still get dereferenced (e.g. if a child task gets executed). So dereference it
                // immediately to leave it in a clean state. This will ease potential NullPointerException detection (e.g. if
                // an inner task is executed from termination handler of another task).
                if (lRestored) dereferenceEmitter();
            }
            return true;
        }
    }

    /**
     * Contains all the information necessary to restore a single emitter on a task handler (its field and its generated Id).
     */
    private static final class TaskEmitterDescriptor {
        private final Field mEmitterField;
        private final TaskEmitterRef mEmitterRef;

        public TaskEmitterDescriptor(Field pEmitterField, TaskEmitterRef pEmitterRef) {
            mEmitterField = pEmitterField;
            mEmitterRef = pEmitterRef;
        }

        public TaskEmitterRef hasSameType(Field pField) {
            return (pField.getType() == mEmitterField.getType()) ? mEmitterRef : null;
        }

        public boolean usesEmitter(TaskEmitterId pTaskEmitterId) {
            return mEmitterRef.hasSameId(pTaskEmitterId);
        }

        /**
         * Restore reference to the current emitter on the specified task handler.
         * 
         * @param pTaskResult Emitter to restore.
         * @return True if referencing succeed or false else.
         */
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
            } catch (RuntimeException eRuntimeException) {
                throw internalError(eRuntimeException);
            }
        }

        /**
         * Clear reference to the given emitter on the specified task handler.
         * 
         * @param pTaskResult Emitter to dereference.
         */
        public void dereference(TaskResult<?> pTaskResult) {
            try {
                mEmitterField.set(pTaskResult, null);
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw internalError(eIllegalArgumentException);
            } catch (IllegalAccessException eIllegalAccessException) {
                throw internalError(eIllegalAccessException);
            } catch (RuntimeException eRuntimeException) {
                throw internalError(eRuntimeException);
            }
        }

        @Override
        public String toString() {
            return "TaskEmitterDescriptor [mEmitterField=" + mEmitterField + ", mEmitterRef=" + mEmitterRef + "]";
        }
    }

    /**
     * Represents a reference to an emitter. Its goal is to add a level of indirection to the emitter so that several tasks can
     * easily share updates made to an emitter.
     */
    private static final class TaskEmitterRef {
        private final TaskEmitterId mEmitterId;
        private volatile WeakReference<Object> mEmitterRef;

        public TaskEmitterRef(Object pEmitterValue) {
            mEmitterId = null;
            set(pEmitterValue);
        }

        public TaskEmitterRef(TaskEmitterId pEmitterId, Object pEmitterValue) {
            mEmitterId = pEmitterId;
            set(pEmitterValue);
        }

        public boolean hasSameId(TaskEmitterId pTaskEmitterId) {
            return (mEmitterId != null) && mEmitterId.equals(pTaskEmitterId);
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
            return "TaskEmitterRef [mEmitterId=" + mEmitterId + ", mEmitterRef=" + mEmitterRef + "]";
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

    /**
     * Allows changing the way things are synchronized in the code (i.e. everything on the UI-Thread or multi-threaded).
     */
    public interface LockingStrategy {
        void createManager(AndroidTaskManager pAndroidTaskManager);

        Lock createLock();

        void checkCallIsAllowed();
    }

    /**
     * Everything is done on the UI-Thread. No lock required.
     */
    public class UIThreadLockingStrategy implements LockingStrategy {
        private Looper mUILooper;

        public UIThreadLockingStrategy() {
            super();
            mUILooper = Looper.getMainLooper();
        }

        @Override
        public void createManager(AndroidTaskManager pAndroidTaskManager) {
            pAndroidTaskManager.mContainers = Collections.newSetFromMap(new ConcurrentHashMap<TaskContainer<?, ?, ?>, Boolean>(DEFAULT_CAPACITY));
            pAndroidTaskManager.mEmitters = new ConcurrentHashMap<TaskEmitterId, TaskEmitterRef>(DEFAULT_CAPACITY);
            pAndroidTaskManager.mDescriptors = new AutoCleanMap<TaskHandler, TaskDescriptor<?, ?, ?>>(DEFAULT_CAPACITY);
        }

        @Override
        public Lock createLock() {
            return new EmptyLock();
        }

        @Override
        public void checkCallIsAllowed() {
            if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        }
    }

    /**
     * Tasks and handlers can be executed on any threads concurrently.
     */
    public class MultiThreadLockingStrategy implements LockingStrategy {
        @Override
        public void createManager(AndroidTaskManager pAndroidTaskManager) {
            pAndroidTaskManager.mContainers = Collections.newSetFromMap(new ConcurrentHashMap<TaskContainer<?, ?, ?>, Boolean>(DEFAULT_CAPACITY));
            pAndroidTaskManager.mEmitters = new ConcurrentHashMap<TaskEmitterId, TaskEmitterRef>(DEFAULT_CAPACITY);
            pAndroidTaskManager.mDescriptors = new AutoCleanMap<TaskHandler, TaskDescriptor<?, ?, ?>>(DEFAULT_CAPACITY);
        }

        @Override
        public Lock createLock() {
            return new ReentrantLock();
        }

        @Override
        public void checkCallIsAllowed() {
        }
    }
}