package com.codexperiments.robolabor.task.android;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;

import com.codexperiments.robolabor.exception.InternalException;
import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskIdentity;
import com.codexperiments.robolabor.task.TaskManager;
import com.codexperiments.robolabor.task.TaskProgress;
import com.codexperiments.robolabor.task.TaskResult;

/**
 * TODO Handle timeout.
 * TODO Handle cancellation.
 * TODO Handle progress.
 * TODO Handle non-inner classes.
 * TODO Handle listen().
 * TODO Synchronize handle().
 * TODO Restore reference during onProgress.
 * TODO Create a specific exception.
 */
public class TaskManagerAndroid implements TaskManager
{
    /*private*/ Configuration mConfig;
    // TODO Use concurrent collections.
    /*private*/ List<TaskContainerAndroid<?>> mContainers;
    /*private*/ Map<Object, WeakReference<?>> mEmittersById;

    /*private*/ Handler mUIQueue;
    /*private*/ Thread mUIThread;


    public TaskManagerAndroid(Configuration pConfig) {
        super();
        mConfig = pConfig;
        mContainers = new LinkedList<TaskContainerAndroid<?>>();
        mEmittersById = new HashMap<Object, WeakReference<?>>();
        
        mUIQueue = new Handler(Looper.getMainLooper());
        mUIThread = mUIQueue.getLooper().getThread();
    }

    protected TaskManagerAndroid(TaskManagerAndroid pTaskManager) {
        super();
        mConfig = pTaskManager.mConfig;
        mContainers = pTaskManager.mContainers;
        mEmittersById = pTaskManager.mEmittersById;
        
        mUIQueue = new Handler(Looper.getMainLooper());
        mUIThread = mUIQueue.getLooper().getThread();
    }

    public void start() {
    }

    public void stop() {
    }
    
    @Override
    public void manage(Object pEmitter) {
        // Save the new task emitter in the reference list. Replace the existing one if any according to its id (the old one is
        // considered obsolete). Emitter Id is computed by the configuration strategy. See DefaultConfiguration for an example.
        // Note that an emitter Id can be null if no emitter dereferencement should be applied.
        Object lEmitterId = mConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
            // collector do its job.
            mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
            for (TaskContainerAndroid<?> lTaskContainer : mContainers) {
                tryFinish(lTaskContainer, false);
            }
        }
    }

    @Override
    public void unmanage(Object pEmitter) {
        // Remove an existing task emitter. If the emitter reference (in Java terms) is different from the object to remove, then
        // don't do anything. This could occur if a new object is managed before an older one with the same Id is unmanaged.
        // Typically, this could occur for example if an Activity X starts and then navigates to an Activity B which is,
        // according to Android lifecycle, started before A is stopped (the two activities are alive at the same time during a
        // short period of time).
        // Note that an emitter Id can be null if a task is not an inner class and thus has no outer class.
        Object lEmitterId = mConfig.resolveEmitterId(pEmitter);
        if (lEmitterId != null) {
            WeakReference<?> lWeakRef = mEmittersById.get(lEmitterId);
            if ((lWeakRef != null) && (lWeakRef.get() == pEmitter)) {
                mEmittersById.remove(lEmitterId);
            }
        }
    }

    @Override
    public <TResult> void execute(Task<TResult> pTask) {
        execute(pTask, null);
    }

    protected <TResult> void execute(Task<TResult> pTask, TaskContainerAndroid<?> pParentContainer) {
        if (pTask == null) throw InternalException.illegalCase();
        
        TaskConfiguration lTaskConfig = mConfig.resolveConfiguration(pTask);
        TaskContainerAndroid<TResult> lContainer = new TaskContainerAndroid<TResult>(pTask, lTaskConfig, pParentContainer);
        // Remember all running tasks. 
        mContainers.add(lContainer);
        
        // Eventually run the background task.
        lTaskConfig.getExecutor().execute(lContainer);
    }

    @Override
    public <TResult> boolean listen(TaskResult<TResult> pTaskListener) {
        return true;
    }

    public <TResult> boolean listen(TaskResult<TResult> pTaskListener, TaskContainerAndroid<?> pContainer) {
        return true;
    }

    @Override
    public void notifyProgress(final TaskProgress<?> pProgress) {
        throw InternalException.illegalCase();
    }

    public void notifyProgress(final TaskProgress<?> pProgress, final TaskContainerAndroid<?> pContainer) {
        // Progress is always executed on the UI-Thread but sent from a non-UI-Thread.
        mUIQueue.post(new Runnable() {
            public void run() {
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
     * @param pContainer Container that contains the finished task.
     * @param pConfirmProcessed True to indicate that the task has finished its computation or False if we don't know. This
     *        parameter should be set to true only from the computation thread after onProcess(), when we know for task is over.
     */
    protected <TResult> void tryFinish(final TaskContainerAndroid<TResult> pContainer, final boolean pConfirmProcessed) {
        if (Thread.currentThread() != mUIThread) {
            mUIQueue.post(new Runnable() {
                public void run() {
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

    
    
    private class TaskContainerAndroid<TResult> implements Runnable, TaskManager {
        // Container info.
        Task<TResult> mTask;
        Object mTaskId;
        TaskConfiguration mConfig;
        boolean mIsInner;
        TaskContainerAndroid<?> mParentContainer;
        
        // Cached values.
        Field mEmitterField;
        Object mEmitterId;

        // Task result and state.
        TResult mResult;
        Throwable mThrowable;
        boolean mProcessed;
        boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask,
                                    TaskConfiguration pConfig,
                                    TaskContainerAndroid<?> pParentContainer) {
            super();
            mTask = pTask;
            mTaskId = (pTask instanceof TaskIdentity) ? ((TaskIdentity) pTask).getId() : null;
            mConfig = pConfig;
            mIsInner = pTask.getClass().getEnclosingClass() != null;
            mParentContainer = pParentContainer;
            
            mEmitterField = resolveEmitterField();
            mEmitterId = resolveEmitterId();

            mResult = null;
            mThrowable = null;
            mProcessed = false;
            mFinished = false;
            
            saveEmitter();
        }

        /**
         * Locate the outer object reference Field (e.g. this$0) inside the task class.
         * @param pTask Object on which the field must be located.
         * @return Field pointing to the outer object or null if pTask is not an inner-class.
         */
        private Field resolveEmitterField() {
            if (mIsInner) {
                Field[] lFields = mTask.getClass().getDeclaredFields();
                for (Field lField : lFields) {
                    String lFieldName = lField.getName();
                    if (lFieldName.startsWith("this$")) {
                        lField.setAccessible(true);
                        return lField;
                    }
                }
                throw InternalException.illegalCase();
            }
            return null;
        }

        /**
         * Find the Id of the outer object owning the task.
         * @param pTask Object on which the field must be located.
         * @return Field pointing to the outer object or null if pTask is not an inner-class.
         */
        private Object resolveEmitterId() {
            if (mIsInner) {
              Object lEmitter = mEmitterField.get(mTask);
              if (lEmitter == null) throw InternalException.illegalCase();
//              if (lEmitter == null) return null;
              mEmitterId = mConfig.resolveEmitterId(lEmitter);
              if (mEmitterId == null) throw InternalException.illegalCase();
          }
        }
        
        private void saveEmitter() {
            try {
                // Dereference the outer emitter.
                Object lEmitter = mEmitterField.get(mTask);
                if (lEmitter == null) throw InternalException.illegalCase();
                // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
                // collector do its job.
                mEmittersById.put(mEmitterId, new WeakReference<Object>(lEmitter));
                // Remove existing outer emitter reference contained in the task.
                dereferenceEmitter();
            } catch (IllegalAccessException eIllegalAccessException) {
                throw InternalException.illegalCase();
            }
        }

        /**
         * Dereference the emitter (which is an outer class) from the task (which is an inner class) and return its Id. Emitter
         * references are stored internally so that they can be restored later when the task has finished its computation.
         * Note that an emitter Id can be null if a task is not an inner class or if no dereferencement should be applied.
         * @param pTask Task to dereference.
         * @return Id of the emitter.
         */
        protected Object dereferenceEmitter() {
            try {
                // Dereference the outer emitter.
                mEmitterField.set(mTask, null);
                return mEmitterId;
            } catch (IllegalAccessException eIllegalAccessException) {
                throw InternalException.illegalCase();
            }
        }

        /**
         * Restore the emitter back into the task.
         * @param pContainer Container that contains the task to restore.
         * @return True if restoration could be performed properly. This may be false if a previously managed object become unmanaged
         *         meanwhile.
         */
        protected boolean referenceEmitter() {
            try {
                // TODO Handle the case of non-inner classes.
                if (mIsInner/* && (mEmitterField != null) && (mEmitterId != null)*/) {
                    WeakReference<?> lEmitterRef = mEmittersById.get(mEmitterId);
                    if (lEmitterRef != null || !mConfig.keepResultOnHold()) {
                        if (mEmitterField != null) {
                            // TODO Check lEmitterRef.get() returns not null value.
                            mEmitterField.set(mTask, lEmitterRef.get());
                            return true;
                        }
                    }
                }
                return false;
            } catch (IllegalAccessException eIllegalAccessException) {
                throw InternalException.illegalCase();
            }
        }

        // On Executor-thread
        public void run() {
            try {
                mResult = mTask.onProcess(this);
            } catch (final Exception eException) {
                mThrowable = eException;
            } finally {
                TaskManagerAndroid.this.tryFinish(this, true);
            }
        }
        
        protected boolean finish(boolean pConfirmProcessed) {
            if (pConfirmProcessed) {
                mProcessed = true;
            }
            if (mProcessed && !mFinished && referenceEmitter()) {
                try {
                    if (mThrowable == null) {
                        mTask.onFinish(this, mResult);
                    } else {
                        mTask.onError(this, mThrowable);
                    }
                } catch (Exception eException) {
                    // TODO Keep ? mTask.onError(this, eException);
                } finally {
                    mFinished = true;
                }
                return true;
            } else {
                return false;
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
        public <TOtherResult> void execute(Task<TOtherResult> pTask) {
            TaskManagerAndroid.this.execute(pTask, this);
        }

        @Override
        public <TOtherResult> boolean listen(TaskResult<TOtherResult> pTaskListener) {
            return TaskManagerAndroid.this.listen(pTaskListener, this);
        }

        @Override
        public void notifyProgress(TaskProgress<?> pProgress) {
            TaskManagerAndroid.this.notifyProgress(pProgress, this);
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



    public interface Configuration {
        Object resolveEmitterId(Object pEmitter);

        TaskConfiguration resolveConfiguration(Task<?> pTask);
    }

    public interface TaskConfiguration {
        ExecutorService getExecutor();

        boolean keepResultOnHold();
    }

    /**
     * Example configuration that handles basic Android components: Activity and Fragments.
     */
    public static class DefaultConfiguration implements Configuration {
        /**
         * Task configuration to execute tasks one by one in the order they were submitted, like a queue. This emulates the
         * AsyncTask behavior used since Android Gingerbread.
         */
        protected TaskConfiguration mSerialConfiguration = buildTaskConfiguration();
        
        /**
         * Create an instance of the executor used to execute tasks. Returned executor is single-threaded and executes tasks
         * sequentially.
         * @return Instance of the serial executor.
         */
        protected TaskConfiguration buildTaskConfiguration() {
            return new TaskConfiguration() {
                /*private*/ ExecutorService mSerialExecutor = buildExecutor();
                
                @Override
                public boolean keepResultOnHold() {
                    return true;
                }
                
                @Override
                public ExecutorService getExecutor() {
                    return mSerialExecutor;
                }
            };
        }
        
        /**
         * Create an instance of the executor used to execute tasks. Returned executor is single-threaded and executes tasks
         * sequentially. This method is called by buildTaskConfiguration() to initialize TaskConfiguration object.
         * @return Instance of the serial executor.
         */
        protected ExecutorService buildExecutor() {
            return Executors.newSingleThreadExecutor(new ThreadFactory() {
                public Thread newThread(Runnable pRunnable) {
                    Thread thread = new Thread(pRunnable);
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }

        @Override
        public Object resolveEmitterId(Object pEmitter) {
            
            if (pEmitter instanceof Activity) {
                return resolveActivityId((Activity) pEmitter);
            } else if (pEmitter instanceof Fragment) {
                return resolveFragmentId((Fragment) pEmitter);
            }
            return resolveDefaultId(pEmitter);
        }

        /**
         * Typically, an Android Activity is identified by its class type: if we start a task X in an activity of type A, navigate
         * to an Activity of type B and finally go back to an activity of type A (which could have been recreated meanwhile), then
         * we want any pending task emitted by the 1st Activity A to be attached again to any further Activity of the same type.
         * 
         * @param pActivity Activity to find the Id of.
         * @return Activity class.
         */
        protected Object resolveActivityId(Activity pActivity) {
            return pActivity.getClass();
        }
        
        /**
         * Typically, an Android Fragment is identified either by an Id (the Id of the component it is inserted in) or a Tag (
         * which is a String).If none of these elements is available, then 
         * 
         * @param pFragment Fragment to find the Id of.
         * @return Fragment Id if not 0, Fragment Tag if not empty or else its Fragment class.
         */
        protected Object resolveFragmentId(Fragment pFragment) {
            if (pFragment.getId() > 0) {
                // TODO An Integer Id is not something unique. Need to append the class type too.
                return pFragment.getId();
            } else if (pFragment.getTag() != null && !pFragment.getTag().isEmpty()) {
                return pFragment.getTag();
            } else {
                return pFragment.getClass();
            }
        }
        
        /**
         * If no information is available, the emitter class is used by default as an Id.
         * 
         * @param pEmitter Emitter to find the Id of.
         * @return Emitter class.
         */
        protected Object resolveDefaultId(Object pEmitter) {
            return pEmitter.getClass();
        }

        @Override
        public TaskConfiguration resolveConfiguration(Task<?> pTask) {
            return mSerialConfiguration;
        }
    }
}
