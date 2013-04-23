package com.codexperiments.robolabor.task.android.configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.app.Activity;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid.ManagerConfiguration;
import com.codexperiments.robolabor.task.android.TaskManagerAndroid.TaskConfiguration;

/**
 * Example configuration that handles basic Android components: Activity and Fragments.
 */
public class DefaultConfiguration implements ManagerConfiguration {
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
        } else if (pEmitter instanceof android.support.v4.app.Fragment) {
            return resolveFragmentId((android.support.v4.app.Fragment) pEmitter);
        } else if (pEmitter instanceof android.app.Fragment) {
            return resolveFragmentId((android.app.Fragment) pEmitter);
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
    protected Object resolveFragmentId(android.support.v4.app.Fragment pFragment) {
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
     * Typically, an Android Fragment is identified either by an Id (the Id of the component it is inserted in) or a Tag (
     * which is a String).If none of these elements is available, then 
     * 
     * @param pFragment Fragment to find the Id of.
     * @return Fragment Id if not 0, Fragment Tag if not empty or else its Fragment class.
     */
    protected Object resolveFragmentId(android.app.Fragment pFragment) {
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