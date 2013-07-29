package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.emitterIdCouldNotBeDetermined;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.emitterNotManaged;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.innerTasksNotAllowed;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.internalError;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.invalidEmitterId;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.mustBeExecutedFromUIThread;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.notCalledFromTask;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.taskExecutedFromUnexecutedTask;
import static com.codexperiments.robolabor.task.android.TaskManagerExceptionAndroid.unmanagedEmittersNotAllowed;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskManagerConfig;
import com.codexperiments.robolabor.task.TaskManagerException;
import com.codexperiments.robolabor.task.TaskRef;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskCallback;
import com.codexperiments.robolabor.task.handler.TaskIdentifiable;
import com.codexperiments.robolabor.task.handler.TaskNotifier;
import com.codexperiments.robolabor.task.handler.TaskProgress;
import com.codexperiments.robolabor.task.handler.TaskResult;
import com.codexperiments.robolabor.task.handler.TaskStart;
import com.codexperiments.robolabor.task.id.TaskId;
import com.codexperiments.robolabor.task.util.AutoCleanMap;

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
public class TaskManagerAndroid implements TaskManager {
    private static final int DEFAULT_CAPACITY = 64;
    // To generate task references.
    private static int TASK_REF_COUNTER;

    private TaskScheduler mDefaultScheduler;
    private TaskManagerConfig mConfig;
    // All the current running tasks.
    private Set<TaskContainer<?>> mContainers;
    // Keep tracks of all emitters. Note that TaskEmitterRef uses a weak reference to avoid memory leaks. This Map is never
    // cleaned and accumulates references because it assumes that any object that managed object set doesn't grow infinitely but
    // is rather limited (e.g. typically all fragments, activity and manager in an Application).
    private Map<TaskEmitterId, TaskEmitterRef> mEmitters;
    // Allows getting back an existing descriptor through its callback when dealing with nested tasks. An AutoCleanMap is
    // neceassary since there is no way to know when a callback are not necessary anymore.
    private Map<TaskCallback, TaskDescriptor<?>> mDescriptors;

    static {
        TASK_REF_COUNTER = Integer.MIN_VALUE;
    }

    public TaskManagerAndroid(Application pApplication, TaskManagerConfig pConfig) {
        super();

        mDefaultScheduler = new TaskScheduler();
        mConfig = pConfig;
        mContainers = Collections.newSetFromMap(new ConcurrentHashMap<TaskContainer<?>, Boolean>(DEFAULT_CAPACITY));
        mEmitters = new ConcurrentHashMap<TaskEmitterId, TaskEmitterRef>(DEFAULT_CAPACITY);
        mDescriptors = new AutoCleanMap<TaskCallback, TaskDescriptor<?>>(DEFAULT_CAPACITY);
    }

    @Override
    public void manage(Object pEmitter) {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");
        mDefaultScheduler.checkCurrentThread();

        // Save the new emitter in the reference list. Replace the existing one, if any, according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. Note that an emitter Id can be null if no
        // emitter dereferencing should be applied.
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

        // Try to terminate any task we can, which is possible if the new registered object is one of their emitter.
        // TODO Maybe we should add a reference from the TaskEmitterId to the client containers.
        for (TaskContainer<?> lContainer : mContainers) {
            lContainer.manage(lEmitterId);
        }
    }

    @Override
    public void unmanage(Object pEmitter) {
        if (pEmitter == null) throw new NullPointerException("Emitter is null");
        mDefaultScheduler.checkCurrentThread();

        // Remove an existing task emitter. If the emitter reference (in Java terms) is different from the object to remove, then
        // don't do anything. This could occur if a new object is managed before an older one with the same Id is unmanaged.
        // Typically, this could occur for example if an Activity X starts and then navigates to an Activity B which is,
        // according to Android lifecycle, started before A is stopped (the two activities are alive at the same time during a
        // short period of time).
        Object lEmitterIdValue = mConfig.resolveEmitterId(pEmitter);
        if (lEmitterIdValue != null) {
            TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
            TaskEmitterRef lEmitterRef = mEmitters.get(lEmitterId);
            if ((lEmitterRef != null) && (lEmitterRef.get() == pEmitter)) {
                lEmitterRef.clear();
            }
        }
    }

    /**
     * Called internally from prepareToRun(). Find the Id of the emitter. If the emitter is not managed, then return an unmanaged
     * reference.
     * 
     * @param pEmitter Emitter to find the reference of.
     * @return Emitter reference. No null is returned.
     * @throws TaskManagerException If the emitter isn't managed (managed by configuration but manage() not called yet).
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
            // update reference himself through manage(Object). But if emitter is managed (i.e. emitter Id returned by
            // configuration) but is not in the emitter list, then a call to manage() is missing.
            if (lEmitterRef == null) throw emitterNotManaged(lEmitterIdValue, pEmitter);
        }
        // Unmanaged emitter case.
        else {
            if (mConfig.allowUnmanagedEmitters()) throw unmanagedEmittersNotAllowed(pEmitter);
            lEmitterRef = new TaskEmitterRef(pEmitter);
        }
        return lEmitterRef;
    }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask) {
        return execute(pTask, pTask);
    }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask, TaskResult<TResult> pTaskResult) {
        if (pTask == null) throw new NullPointerException("Task is null");
        mDefaultScheduler.checkCurrentThread();

        // Create a container to run the task.
        TaskContainer<TResult> lContainer = new TaskContainer<TResult>(pTask, mDefaultScheduler, mConfig);
        // Remember and run the new task. If an identical task is already executing, do nothing.
        if (mContainers.add(lContainer)) {
            // Prepare the task (i.e. cache needed values) before adding it because the first operation can fail (and we don't
            // want to leave the container list in an incorrect state).
            TaskRef<TResult> lTaskRef = lContainer.prepareToRun(pTaskResult);
            mConfig.resolveExecutor(pTask).execute(lContainer);
            return lTaskRef;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <TResult> boolean rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult) {
        if (pTaskRef == null) throw new NullPointerException("Task is null");
        if (pTaskResult == null) throw new NullPointerException("TaskResult is null");
        mDefaultScheduler.checkCurrentThread();

        // TODO It seems possible to add a reference from the TaskRef to the TaskContainer to avoid this lookup.
        for (TaskContainer<?> lContainer : mContainers) {
            // Cast safety is guaranteed by the execute() method that returns a properly typed TaskId for the new container.
            ((TaskContainer<TResult>) lContainer).rebind(pTaskRef, pTaskResult);
            return true;
        }
        return false;
    }

    @Override
    public void notifyProgress() {
        throw notCalledFromTask();
    }

    /**
     * Called when task is processed and finished to clean remaining references.
     * 
     * @param pContainer Finished task container.
     */
    protected void notifyFinished(final TaskContainer<?> pContainer) {
        mContainers.remove(pContainer);
    }

    /**
     * Contains all the information about the task to execute.
     */
    private class TaskContainer<TResult> implements Runnable, TaskNotifier {
        // Handlers
        private Task<TResult> mTask;

        // Container info.
        private volatile TaskDescriptor<TResult> mDescriptor;
        private final TaskRef<TResult> mTaskRef;
        private final TaskId mTaskId;
        private final TaskScheduler mScheduler;
        private final TaskManagerConfig mConfig;

        // Task result and state.
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mRunning;
        private boolean mFinished;

        // Cached values.
        private Runnable mProgressRunnable;

        public TaskContainer(Task<TResult> pTask, TaskScheduler pScheduler, TaskManagerConfig pConfig) {
            super();
            mTask = pTask;

            mDescriptor = null;
            mTaskRef = new TaskRef<TResult>(TASK_REF_COUNTER++);
            mTaskId = (pTask instanceof TaskIdentifiable) ? ((TaskIdentifiable) pTask).getId() : null;
            mScheduler = pScheduler;
            mConfig = pConfig;

            mResult = null;
            mThrowable = null;
            mRunning = true;
            mFinished = false;

            prepareCallbacks();
        }

        /**
         * Initialize the container (i.e. cache needed values, ...) before running it.
         */
        protected TaskRef<TResult> prepareToRun(TaskResult<TResult> pTaskResult) {
            final TaskDescriptor<TResult> lDescriptor = new TaskDescriptor<TResult>(pTaskResult);
            if (!lDescriptor.isSameAsTask(mTask)) {
                prepareTask();
            }
            lDescriptor.prepareToRun();
            mScheduler.scheduleCallbackIfNecessary(new Runnable() {
                public void run() {
                    lDescriptor.onStart(true);
                }
            });

            mDescriptor = lDescriptor;
            mDescriptors.put(pTaskResult, lDescriptor); // TODO Check for optim
            return mTaskRef;
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

        private void prepareCallbacks() {
            mProgressRunnable = new Runnable() {
                public void run() {
                    mDescriptor.onProgress();
                }
            };
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
                mScheduler.scheduleCallback(new Runnable() {
                    public void run() {
                        mRunning = false;
                        finish();
                    }
                });
            }
        }

        /**
         * Calls onStart() when a task is rebound to another object or when an emitter gets managed.
         * 
         * @param pTaskEmitterId
         */
        private void restore(final TaskDescriptor<TResult> pDescriptor) {
            mScheduler.scheduleCallbackIfNecessary(new Runnable() {
                public void run() {
                    if (!finish()) {
                        pDescriptor.onStart(true);
                    }
                }
            });
        }

        protected void manage(TaskEmitterId pEmitterId) {
            final TaskDescriptor<TResult> lDescriptor = mDescriptor;
            if (lDescriptor.matchesId(pEmitterId)) {
                restore(lDescriptor);
            }
        }

        /**
         * Replace the previous task handler (usually implemented in the task itself) with a new one. Previous handler is lost.
         * 
         * @param pTaskRef Reference of the task to rebind to. If different, nothing is performed.
         * @param pTaskResult Task handler that must replace previous one.
         */
        protected void rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult) {
            if (mTaskRef.equals(pTaskRef)) {
                final TaskDescriptor<TResult> lDescriptor = new TaskDescriptor<TResult>(pTaskResult);
                lDescriptor.prepareToRun();
                restore(lDescriptor);

                mDescriptor = lDescriptor;
                mDescriptors.put(pTaskResult, lDescriptor); // TODO Check for optim
            }
        }

        /**
         * Try to execute task termination handlers (i.e. onFinish and onFail). The latter may not be executed if at least one of
         * the outer object reference can't be restored. When the task is effectively finished, set the corresponding flag to
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
        private boolean finish() {
            // Execute task termination handlers if they have not been yet (but only if the task has been fully processed).
            if (mRunning) return false;
            if (mFinished) return true;

            TaskDescriptor<TResult> lDescriptor = mDescriptor;
            // TODO Don't like the configuration parameter here.
            mFinished = lDescriptor.onFinish(mResult, mThrowable, mConfig.keepResultOnHold(mTask));
            if (mFinished) {
                notifyFinished(this);
            }
            return mFinished;
        }

        @Override
        public void notifyProgress() {
            // Progress is always executed on the UI-Thread but sent from a non-UI-Thread (except if called from onFinish() or
            // onFail() but that shouldn't occur often).
            mScheduler.scheduleCallback(mProgressRunnable);
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

    private static class TaskScheduler {
        private Handler mUIQueue;
        private Looper mUILooper;

        public TaskScheduler() {
            super();
            mUILooper = Looper.getMainLooper();
            mUIQueue = new Handler(mUILooper);
        }

        public void checkCurrentThread() {
            if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
        }

        public void scheduleCallback(Runnable pCallbackRunnable) {
            mUIQueue.post(pCallbackRunnable);
        }

        public void scheduleCallbackIfNecessary(Runnable pCallbackRunnable) {
            if (Looper.myLooper() == mUILooper) {
                pCallbackRunnable.run();
            } else {
                mUIQueue.post(pCallbackRunnable);
            }
        }
    }

    /**
     * Contains all the information necessary to restore all the emitters (even parent emitters) of a task. Once prepareToRun() is
     * called, the content of this class is not modified anymore (except the emitter and the reference counter dedicated to
     * referencing and dereferencing).
     */
    private final class TaskDescriptor<TResult> {
        private final TaskResult<TResult> mTaskResult;
        private final List<TaskEmitterDescriptor> mEmitterDescriptors;
        private Set<TaskDescriptor<?>> mParentDescriptors; // Not final because lazy-initialized.
        // Counts the number of time a task has been referenced without being dereferenced. A task will be dereferenced only when
        // this counter reaches 0, which means that no other task needs references to be set. This situation can occur for example
        // when starting a child task from a parent task callback (e.g. in onFinish()): when the child task is launched, it must
        // not dereference emitters because the parent task is still in its onFinish() callback and may need references to them.
        private int mReferenceCounter;

        public TaskDescriptor(TaskResult<TResult> pTaskResult) {
            mTaskResult = pTaskResult;
            mEmitterDescriptors = new ArrayList<TaskEmitterDescriptor>(1); // Most of the time, a task will have only one emitter.
            mParentDescriptors = null;
            mReferenceCounter = 0;
        }

        public boolean matchesId(TaskEmitterId pEmitterId) {
            if (mTaskResult instanceof TaskStart) {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    if (lEmitterDescriptor.hasSameId(pEmitterId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isSameAsTask(Task<TResult> pTask) {
            return pTask == mTaskResult;
        }

        /**
         * Initialize the container (i.e. cache needed values, ...) before running it.
         */
        protected void prepareToRun() {
            try {
                prepareTaskResult();
            } finally {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    lEmitterDescriptor.dereference(mTaskResult);
                }
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
                            prepareEmitterField(lField);
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

        private void lookForParentDescriptor(Field pField, Object pEmitter) {
            if (TaskCallback.class.isAssignableFrom(pField.getType())) {
                TaskDescriptor<?> lDescriptor = mDescriptors.get(pEmitter);
                if (lDescriptor == null) throw taskExecutedFromUnexecutedTask(pEmitter);

                if (mParentDescriptors == null) {
                    // A task will have most of the time no parents. Hence lazy-initialization. But if that's not the case, then a
                    // task will usually have only one parent, rarely more.
                    mParentDescriptors = new HashSet<TaskManagerAndroid.TaskDescriptor<?>>(1);
                }
                mParentDescriptors.add(lDescriptor);
            } else {
                try {
                    // Go through the main class and each of its super classes and look for "this$" fields.
                    Class<?> lTaskResultClass = pEmitter.getClass();
                    while (lTaskResultClass != Object.class) {
                        // If current class is an inner class...
                        if ((lTaskResultClass.getEnclosingClass() != null) && !Modifier.isStatic(lTaskResultClass.getModifiers())) {
                            // Find emitter references and manage them.
                            for (Field lField : lTaskResultClass.getDeclaredFields()) {
                                if (lField.getName().startsWith("this$")) {
                                    lField.setAccessible(true);
                                    Object lParentEmitter = lField.get(pEmitter);
                                    if (lParentEmitter != null) {
                                        lookForParentDescriptor(lField, lParentEmitter);
                                    } else {
                                        findEmitterRefInParentContainers(lField);
                                    }
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

        private TaskEmitterRef findEmitterRefInParentContainers(Field pField) {
            if (mParentDescriptors != null) {
                for (TaskDescriptor<?> lParentDescriptor : mParentDescriptors) {
                    TaskEmitterRef lEmitterRef;
                    for (TaskEmitterDescriptor lParentEmitterDescriptor : lParentDescriptor.mEmitterDescriptors) {
                        lEmitterRef = lParentEmitterDescriptor.hasSameType(pField);
                        if (lEmitterRef != null) return lEmitterRef;
                    }

                    lEmitterRef = lParentDescriptor.findEmitterRefInParentContainers(pField);
                    if (lEmitterRef != null) return lEmitterRef;
                }
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
            if (mParentDescriptors != null) {
                for (TaskDescriptor<?> lParentDescriptor : mParentDescriptors) {
                    lParentDescriptor.dereferenceEmitter();
                }
            }

            synchronized (this) {
                // Note: No need to rollback modifications if an exception occur. Leave references as is, thus creating a memory
                // leak. We can't do much about it since having an exception here denotes an internal bug.
                if ((--mReferenceCounter) == 0) {
                    for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                        lEmitterDescriptor.dereference(mTaskResult);
                    }
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
            // Try to restore emitters in parent containers first. Everything is rolled-back if referencing fails.
            boolean lRestored = true;
            if (mParentDescriptors != null) {
                for (TaskDescriptor<?> lParentDescriptor : mParentDescriptors) {
                    lRestored &= lParentDescriptor.referenceEmitter();
                }
            }

            // Restore references for current container if referencing succeeded previously.
            if (lRestored) {
                synchronized (this) {
                    try {
                        if ((mReferenceCounter++) == 0) {
                            for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                                lRestored &= lEmitterDescriptor.reference(mTaskResult);
                            }
                        }
                    }
                    // Note: Rollback any modifications if an exception occurs. Having an exception here denotes an internal bug.
                    catch (TaskManagerException eTaskManagerException) {
                        // Note that this may theoretically fail too. In that case, we may be completely stuck with an invalid
                        // internal state. However, such a case happen, we can hope the problem will happen at the same point in
                        // both referenceEmitter() and dereferenceEmitter() (more specifically when accessing fields by
                        // reflection). In that case, integrity will be hopefully preserved.
                        dereferenceEmitter();
                        throw eTaskManagerException;
                    }
                }
            }
            return lRestored;
        }

        public void onStart(boolean pIsRestored) {
            if (mTaskResult instanceof TaskStart) {
                if (referenceEmitter()) {
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

        public void onProgress() {
            if (mTaskResult instanceof TaskProgress) {
                if (referenceEmitter()) {
                    try {
                        ((TaskProgress) mTaskResult).onProgress();
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
            boolean lRestored = true;
            if (!referenceEmitter()) {
                if (pKeepResultOnHold) {
                    return false;
                } else {
                    lRestored = false;
                }
            }

            // Run termination callbacks.
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
     * Contains all the information necessary to restore an emitter on a task handler (its field and its generated Id).
     */
    private static final class TaskEmitterDescriptor {
        private final Field mEmitterField;
        private final TaskEmitterRef mEmitterRef;

        public TaskEmitterDescriptor(Field pEmitterField, TaskEmitterRef pEmitterRef) {
            mEmitterField = pEmitterField;
            mEmitterRef = pEmitterRef;
        }

        public boolean hasSameId(TaskEmitterId pTaskEmitterId) {
            return mEmitterRef.hasSameId(pTaskEmitterId);
        }

        public TaskEmitterRef hasSameType(Field pField) {
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
            } catch (RuntimeException eRuntimeException) {
                throw internalError(eRuntimeException);
            }
        }

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
            return mEmitterId.equals(pTaskEmitterId);
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