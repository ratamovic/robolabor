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
import java.util.concurrent.ConcurrentHashMap;

import android.app.Application;
import android.content.Intent;
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
    private TaskScheduler mDefaultScheduler;
    private Intent mServiceIntent;

    private TaskManagerConfig mConfig;
    // All the current running tasks.
    private Set<TaskContainer<?>> mContainers;
    // Keep tracks of all emitters.
    private Map<TaskEmitterId, TaskEmitterRef> mEmittersById;
    private Map<TaskCallback, TaskDescriptor<?>> mCallbackRegister; // TODO Cleanup

    static {
        TASK_REF_COUNTER = Integer.MIN_VALUE;
    }

    public TaskManagerAndroid(Application pApplication, TaskManagerConfig pConfig) {
        super();

        mApplication = pApplication;
        mDefaultScheduler = new TaskScheduler();
        // Because a service is created, the client application dies if the UI-Thread stops for a long time (an ANR). This
        // shouldn't happen at runtime except when running in Debug mode with a breakpoint placed in the UI-Thread. Thus, service
        // is disabled in Debug mode. I don't think this is a good idea but for now this is the simplest thing to do.
        mServiceIntent = new Intent(mApplication, TaskManagerServiceAndroid.class);

        mConfig = pConfig;
        mContainers = new HashSet<TaskContainer<?>>(); // TODO Sync
        mEmittersById = new HashMap<TaskEmitterId, TaskEmitterRef>(); // TODO Sync
        mCallbackRegister = new ConcurrentHashMap<TaskCallback, TaskDescriptor<?>>();
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

        // Save the reference of the emitter. A weak reference is used to avoid memory leaks.
        TaskEmitterId lEmitterId = new TaskEmitterId(pEmitter.getClass(), lEmitterIdValue);
        TaskEmitterRef lEmitterRef = mEmittersById.get(lEmitterId);
        if (lEmitterRef == null) {
            lEmitterRef = mEmittersById.put(lEmitterId, new TaskEmitterRef(lEmitterId, pEmitter));
        } else {
            lEmitterRef.set(pEmitter);
        }

        // Try to terminate any task we can, which is possible if the new registered object is one of their emitter.
        for (TaskContainer<?> lContainer : mContainers) {
            if (!lContainer.finish()) {
                lContainer.restore(lEmitterId);
            }
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
    // TODO Sync
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

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask) {
        return execute(pTask, pTask);
    }

    @Override
    public <TResult> TaskRef<TResult> execute(Task<TResult> pTask, TaskResult<TResult> pTaskResult) {
        if (pTask == null) throw new NullPointerException("Task is null");
        mDefaultScheduler.checkCurrentThread();

        // Create a container to run the task.
        TaskContainer<TResult> lContainer = new TaskContainer<TResult>(pTask, pTaskResult, mDefaultScheduler, mConfig);
        // Remember and run the new task. If an identical task is already executing, do nothing to prevent duplicate tasks.
        if (!mContainers.contains(lContainer)) { // TODO Sync Problem here since we dereference already here.
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

    @SuppressWarnings("unchecked")
    public <TResult> boolean rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult) {
        if (pTaskRef == null) throw new NullPointerException("Task is null");
        if (pTaskResult == null) throw new NullPointerException("TaskResult is null");
        mDefaultScheduler.checkCurrentThread();

        for (TaskContainer<?> lContainer : mContainers) {
            if (lContainer.hasSameRef(pTaskRef)) {
                // Cast safety is guaranteed by the execute() method that returns a properly typed TaskId for the new container.
                ((TaskContainer<TResult>) lContainer).rebind(pTaskResult);
                // If Rebound task is over, execute newly attached handler.
                lContainer.finish();
                return true;
            }
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
    // TODO Sync
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
    private class TaskContainer<TResult> implements Runnable, TaskNotifier {
        // Handlers
        private Task<TResult> mTask;

        // Container info.
        private TaskRef<TResult> mTaskRef;
        private TaskDescriptor<TResult> mDescriptor;
        private TaskId mTaskId;
        private TaskScheduler mScheduler;
        private TaskManagerConfig mConfig;

        // Task result and state.
        private TResult mResult;
        private Throwable mThrowable;
        private boolean mProcessed;
        private boolean mFinished;

        // Cached values.
        private Runnable mProgressRunnable;

        public TaskContainer(Task<TResult> pTask,
                             TaskResult<TResult> pTaskResult,
                             TaskScheduler pScheduler,
                             TaskManagerConfig pConfig)
        {
            super();
            mTask = pTask;

            mTaskRef = new TaskRef<TResult>(TASK_REF_COUNTER++);
            mDescriptor = new TaskDescriptor<TResult>(pTaskResult);
            mTaskId = (pTask instanceof TaskIdentifiable) ? ((TaskIdentifiable) pTask).getId() : null;
            mScheduler = pScheduler;
            mConfig = pConfig;

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;

            mProgressRunnable = prepareProgress();
        }

        /**
         * Initialize the container (i.e. cache needed values, ...) before running it.
         */
        protected TaskRef<TResult> prepareToRun(boolean pIsRestored) {
            TaskDescriptor<TResult> lDescriptor = mDescriptor;
            lDescriptor.onStart(false);

            if (!lDescriptor.isSameAsTask(mTask)) {
                prepareTask();
            }
            lDescriptor.prepareToRun();
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
                        mProcessed = true;
                        finish();
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
            TaskDescriptor<TResult> lDescriptor = null;
            boolean deref = false; // TODO codeporc
            try {
                synchronized (this) {
                    lDescriptor = mDescriptor;
                    if (lDescriptor.matchesId(pTaskEmitterId)) {
                        if (!lDescriptor.referenceEmitter()) {
                            return;
                        }
                        deref = true;
                    }
                }

                mDescriptor.onStart(true); // TODO Schedule
            }
            // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
            catch (RuntimeException eRuntimeException) {
                if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
            } finally {
                synchronized (this) {
                    if (deref) lDescriptor.dereferenceEmitter();
                }
            }
        }

        /**
         * Replace the previous task handler (usually implemented in the task itself) with a new one. Previous handler is lost.
         * 
         * @param pTaskResult Task handler that must replace previous one.
         * @param pParentContainer Context in which the task handler is executed.
         */
        protected void rebind(TaskResult<TResult> pTaskResult) {
            // TODO Sync
            TaskDescriptor<TResult> lDescriptor = new TaskDescriptor<TResult>(pTaskResult);
            lDescriptor.onStart(true);
            mDescriptor.prepareToRun();
            synchronized (this) {
                mDescriptor = lDescriptor;
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
        protected boolean finish() {
            // Execute task termination handlers if they have not been yet (but only if the task has been fully processed).
            TaskDescriptor<TResult> lDescriptor = null;
            try {
                synchronized (this) {
                    if (!mProcessed || mFinished) return false;

                    lDescriptor = mDescriptor;
                    if (!lDescriptor.referenceEmitter() && mConfig.keepResultOnHold(mTask)) {
                        return false;
                    } else {
                        mFinished = true;
                        notifyFinished(this); // TODO Sync
                    }
                }

                try {
                    if (mThrowable == null) {
                        lDescriptor.onFinish(/* this, */mResult);
                    } else {
                        lDescriptor.onFail(/* this, */mThrowable);
                    }
                }
                // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
                catch (RuntimeException eRuntimeException) {
                    if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                }
                return true;
            } finally {
                // After task is over, it may still get dereferenced (e.g. if a child task gets executed). So dereference it
                // immediately to leave it in a clean state. This will ease potential NullPointerException detection (e.g. if
                // an inner task is executed from termination handler of another task).
                synchronized (this) {
                    if (lDescriptor != null) lDescriptor.dereferenceEmitter();
                }
            }
        }

        private Runnable prepareProgress() {
            return new Runnable() {
                public void run() {
                    TaskDescriptor<TResult> lDescriptor = null;
                    try {
                        synchronized (this) {
                            lDescriptor = mDescriptor;
                            if (!lDescriptor.referenceEmitter()) {
                                return;
                            }
                        }
                        lDescriptor.onProgress(/* TaskContainer.this */);
                    }
                    // An exception occurred inside onFail. We can't do much now except committing a suicide or ignoring it.
                    catch (RuntimeException eRuntimeException) {
                        if (mConfig.crashOnHandlerFailure()) throw eRuntimeException;
                    } finally {
                        synchronized (this) {
                            lDescriptor.dereferenceEmitter();
                        }
                    }
                }
            };
        }

        @Override
        public void notifyProgress() {
            // TODO Comments
            // Progress is always executed on the UI-Thread but sent from a non-UI-Thread (except if called from onFinish() or
            // onFail() but that shouldn't occur often).
            mScheduler.scheduleCallback(mProgressRunnable);
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

    private static class TaskScheduler {
        private Handler mUIQueue;
        private Looper mUILooper;

        public TaskScheduler() {
            super();
            mUILooper = Looper.getMainLooper();
            mUIQueue = new Handler(mUILooper);
        }

        public void checkCurrentThread() {
            if (Looper.myLooper() != mUILooper) {
                throw mustBeExecutedFromUIThread();
            }
        }

        public void scheduleCallback(Runnable pCallbackRunnable) {
            mUIQueue.post(pCallbackRunnable);
        }
    }

    private/* TODO static */final class TaskDescriptor<TResult> {
        private final TaskResult<TResult> mTaskResult;
        private final Set<TaskDescriptor<?>> mParentDescriptors;
        private final List<TaskEmitterDescriptor> mEmitterDescriptors;
        // Counts the number of time a task has been referenced without being dereferenced. A task will be dereferenced only when
        // this counter reaches 0, which means that no other task needs references to be set. This situation can occur for example
        // when starting a child task from a parent task callback (e.g. in onFinish()): when the child task is launched, it must
        // not dereference emitters because the parent task is still in its onFinish() callback and may need references to them.
        private int mReferenceCounter;

        public TaskDescriptor(TaskResult<TResult> pTaskResult/* , TaskDescriptor<?> pParentDescriptor */) {
            mTaskResult = pTaskResult;
            mParentDescriptors = new HashSet<TaskManagerAndroid.TaskDescriptor<?>>();
            mEmitterDescriptors = new ArrayList<TaskEmitterDescriptor>(1); // Most of the time, a task will have only one emitter.
            mReferenceCounter = 0;

            mCallbackRegister.put(pTaskResult, this);
        }

        public boolean matchesId(TaskEmitterId pTaskEmitterId) {
            if (mTaskResult instanceof TaskStart) {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    if (lEmitterDescriptor.hasSameId(pTaskEmitterId)) {
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
                TaskDescriptor<?> lDescriptor = mCallbackRegister.get(pEmitter);
                if (lDescriptor != null) {
                    mParentDescriptors.add(lDescriptor);
                } else {
                    // TODO Throw
                }
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
                                    lField.setAccessible(true); // TODO Use only class
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
            Set<TaskDescriptor<?>> lParentDescriptors = mParentDescriptors;
            if (lParentDescriptors == null) {
                return null;
            } else {
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
            boolean lRestored = true;
            if (mParentDescriptors != null) { // TODO Make null
                for (TaskDescriptor<?> lParentDescriptor : mParentDescriptors) {
                    lRestored &= lParentDescriptor.referenceEmitter();
                }
            }

            // Restore references for current container.
            if ((mReferenceCounter++) == 0) {
                for (TaskEmitterDescriptor lEmitterDescriptor : mEmitterDescriptors) {
                    lRestored &= lEmitterDescriptor.reference(mTaskResult);
                }
            }
            return lRestored;
        }

        public void onStart(boolean pIsRestored) {
            if (mTaskResult instanceof TaskStart) {
                ((TaskStart) mTaskResult).onStart(pIsRestored);
            }
        }

        public void onProgress() {
            if (mTaskResult instanceof TaskProgress) {
                ((TaskProgress) mTaskResult).onProgress();
            }
        }

        public void onFinish(TResult pResult) {
            if (mTaskResult instanceof TaskResult) {
                mTaskResult.onFinish(pResult);
            }
        }

        public void onFail(Throwable pException) {
            if (mTaskResult instanceof TaskResult) {
                mTaskResult.onFail(pException);
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
     * TODO Comments
     */
    private static final class TaskEmitterRef {
        private final TaskEmitterId mEmitterId;
        private WeakReference<?> mEmitterRef; // TODO Sync Dont think so...

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