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
import com.codexperiments.robolabor.task.TaskResult;

/**
 * TODO Handle timeout.
 * TODO Handle cancellation.
 * TODO Handle progress.
 * TODO Handle non-inner classes.
 * TODO Handle listen().
 */
public class TaskManagerAndroid implements TaskManager
{
    private Configuration mConfig;
    // TODO Use concurrent collections.
    private List<TaskContainerAndroid<?>> mContainers;
    private Map<Object, WeakReference<?>> mEmittersById;

    private Handler mUIQueue;
    private Thread mUIThread;


    public TaskManagerAndroid(Configuration pConfig) {
        super();
        mConfig = pConfig;
        mContainers = new LinkedList<TaskContainerAndroid<?>>();
        mEmittersById = new HashMap<Object, WeakReference<?>>();
        
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
            mEmittersById.put(lEmitterId, new WeakReference<Object>(pEmitter));
            for (TaskContainerAndroid<?> lTaskContainer : mContainers) {
                finish(lTaskContainer, false);
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
        TaskConfiguration lTaskConfig = mConfig.resolveConfiguration(pTask);
        // Note that an emitter Id can be null if a task is not an inner class or if no dereferencement should be applied.
        Object lEmitterId = dereferenceEmitter(pTask);
        TaskContainerAndroid<TResult> lContainer = makeContainer(lEmitterId, pTask, lTaskConfig);
        lTaskConfig.getExecutor().execute(lContainer);
    }

    /**
     * Dereference the emitter (which is an outer class) from the task (which is an inner class) and return its Id. Emitter
     * references are stored internally so that they can be restored later when the task has finished its computation.
     * @param pTask Task to dereference.
     * @return Id of the emitter.
     */
    protected Object dereferenceEmitter(Task<?> pTask) {
        try {
            Field lEmitterField = resolveEmitterField(pTask);
            if (lEmitterField == null) return null;
            Object lEmitter = lEmitterField.get(pTask);
            if (lEmitter == null) return null;
            Object lEmitterId = mConfig.resolveEmitterId(lEmitter);
            if (lEmitterId == null) return null;

            // Dereference the outer emitter.
            lEmitterField.set(pTask, null);
            // Save the reference of the emitter class. A weak reference is used to avoid memory leaks and let the garbage
            // collector do its job.
            mEmittersById.put(lEmitterId, new WeakReference<Object>(lEmitter));
            return lEmitterId;
        } catch (IllegalArgumentException eIllegalArgumentException) {
            throw InternalException.illegalCase();
        } catch (IllegalAccessException eIllegalAccessException) {
            throw InternalException.illegalCase();
        }
    }
    
    /**
     * Factory method that creates a container that runs the specified background task.
     * @param pEmitterId Id of the object that emitted the task.
     * @param pTask Task to execute.
     * @param pConfig Configuration of the task.
     * @return The task container.
     */
    protected <TResult> TaskContainerAndroid<TResult> makeContainer(Object pEmitterId,
                                                                    Task<TResult> pTask,
                                                                    TaskConfiguration pConfig) {
        TaskContainerAndroid<TResult> lTaskContainer;
        if (pTask instanceof TaskIdentity) {
            Object lTaskId = ((TaskIdentity) pTask).getId();
            lTaskContainer = new TaskContainerAndroid<TResult>(pTask, pConfig, pEmitterId, lTaskId);
        } else {
            lTaskContainer = new TaskContainerAndroid<TResult>(pTask, pConfig, pEmitterId);
        }
        mContainers.add(lTaskContainer);
        return lTaskContainer;
    }

    @Override
    public <TResult> boolean listen(TaskResult<TResult> pTaskListener) {
        return true;
    }

//    @Override
//    public void notifyProgress(final TaskProgress pProgress) {
//        // Progress is always executed on the UI Thread.
//        mUIQueue.post(new Runnable() {
//            public void run() {
//                // TODO Restore reference during onProgress.
//                pProgress.onProgress(TaskManagerAndroid.this);
//            }
//        });
//    }

    /**
     * Called when task computation has ended. It restores emitters reference if applicable and triggers the right task callbacks.
     * Triggers are executed on the UI Thread.
     * @param pContainer Container that contains the finished task.
     * @param pConfirmProcessed True to indicate that the task has finished its computation or False if we don't know. This
     *        parameter should be set to true only from the computation thread after onProcess(), when we know for task is over.
     */
    protected <TResult> void finish(final TaskContainerAndroid<TResult> pContainer, final boolean pConfirmProcessed) {
        if (Thread.currentThread() != mUIThread) {
            mUIQueue.post(new Runnable() {
                public void run() {
                    finish(pContainer, pConfirmProcessed);
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
            if (pConfirmProcessed) pContainer.mProcessed = true;
            
            if (pContainer.mProcessed && !pContainer.mFinished && referenceEmitter(pContainer)) {
                try {
                    if (pContainer.mThrowable == null) {
                        pContainer.mTask.onFinish(this, pContainer.mResult);
                    } else {
                        pContainer.mTask.onError(this, pContainer.mThrowable);
                    }
                } catch (Exception eException) {
                    pContainer.mTask.onError(this, eException);
                } finally {
                    pContainer.mFinished = true;
                }
            }
        }
    }

    /**
     * Restore the emitter back into the task.
     * @param pContainer Container that contains the task to restore.
     * @return True if restoration could be performed properly. This may be false if a previously managed object become unmanaged
     *         meanwhile.
     */
    protected <TResult> boolean referenceEmitter(TaskContainerAndroid<TResult> pContainer) {
        try {
            // TODO Handle the case of non-inner classes.
            if (pContainer.mEmitterId != null) {
                WeakReference<?> lEmitterRef = mEmittersById.get(pContainer.mEmitterId);
                if (lEmitterRef != null || !pContainer.mConfig.keepResultOnHold()) {
                    Field lEmitterField = resolveEmitterField(pContainer.mTask);
                    if (lEmitterField != null) {
                        lEmitterField.set(pContainer.mTask, lEmitterRef.get());
                        mContainers.remove(this);
                        return true;
                    }
                }
            }
            return false;
        } catch (IllegalArgumentException eIllegalArgumentException) {
            throw InternalException.illegalCase();
        } catch (IllegalAccessException eIllegalAccessException) {
            throw InternalException.illegalCase();
        }
    }

    /**
     * Locate the outer object reference Field (e.g. this$0) inside the inner class.
     * @param pTask Object on which the field must be located.
     * @return Field pointing to the outer object or null if pTask is not an inner-class.
     */
    private Field resolveEmitterField(Object pTask) {
        Field[] lFields = pTask.getClass().getDeclaredFields();
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
        Object mEmitterId;

        Object mTaskId;
        TaskConfiguration mConfig;
        
        TResult mResult;
        Throwable mThrowable;
        boolean mProcessed;
        boolean mFinished;

        public TaskContainerAndroid(Task<TResult> pTask, TaskConfiguration pConfig, Object pEmitterId, Object pTaskId) {
            super();
            mTask = pTask;
            mEmitterId = pEmitterId;
            
            mTaskId = pTaskId;
            mConfig = pConfig;

            mProcessed = false;
            mFinished = false;
        }

        public TaskContainerAndroid(Task<TResult> pTask, TaskConfiguration pConfiguration, Object pEmitterId) {
            super();
            mTask = pTask;
            mEmitterId = pEmitterId;
            
            mTaskId = null;
            mConfig = pConfiguration;

            mProcessed = false;
            mFinished = false;
        }

        // On Executor-thread
        public void run() {
            try {
                mResult = mTask.onProcess(TaskManagerAndroid.this);
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
