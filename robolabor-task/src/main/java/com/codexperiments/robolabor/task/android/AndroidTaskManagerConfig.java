package com.codexperiments.robolabor.task.android;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.text.TextUtils;

import com.codexperiments.robolabor.task.TaskManagerConfig;
import com.codexperiments.robolabor.task.handler.Task;

/**
 * Example configuration that handles basic Android components: Activity and Fragments.
 */
public class AndroidTaskManagerConfig implements TaskManagerConfig {
    private Application mApplication;
    private ExecutorService mSerialExecutor;

    private Class<?> mFragmentClass;
    private Class<?> mFragmentCompatClass;

    public AndroidTaskManagerConfig(Application pApplication) {
        mApplication = pApplication;
        mSerialExecutor = createExecutors();

        ClassLoader lClassLoader = getClass().getClassLoader();
        try {
            mFragmentClass = Class.forName("android.app.Fragment", false, lClassLoader);
        } catch (ClassNotFoundException eClassNotFoundException) {
            // Current platform doesn't seem to support fragments.
        }
        try {
            mFragmentCompatClass = Class.forName("android.support.v4.app.Fragment", false, lClassLoader);
        } catch (ClassNotFoundException eClassNotFoundException) {
            // Current application doesn't embed compatibility library.
        }
    }

    /**
     * Create a single-threaded executor which executes tasks sequentially.
     * 
     * @return Instance of the serial executor.
     */
    protected ExecutorService createExecutors() {
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
        } else if (mFragmentClass != null && mFragmentClass.isInstance(pEmitter)) {
            return resolveFragmentId((android.app.Fragment) pEmitter);
        } else if (mFragmentCompatClass != null && mFragmentCompatClass.isInstance(pEmitter)) {
            return resolveFragmentId((android.support.v4.app.Fragment) pEmitter);
        }
        return null;
    }

    /**
     * Typically, an Android Activity is identified by its class type: if we start a task X in an activity of type A, navigate to
     * an Activity of type B and finally go back to an activity of type A (which could have been recreated meanwhile), then we
     * want any pending task emitted by the 1st Activity A to be attached again to any further Activity of the same type.
     * 
     * @param pActivity Activity to find the Id of.
     * @return Activity class.
     */
    protected Object resolveActivityId(Activity pActivity) {
        return pActivity.getClass();
    }

    /**
     * Typically, an Android Fragment is identified either by an Id (the Id of the component it is inserted in) or a Tag ( which
     * is a String).If none of these elements is available, then Fragment's class is used instead.
     * 
     * @param pFragment Fragment to find the Id of.
     * @return Fragment Id if not 0, Fragment Tag if not empty or else its Fragment class.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected Object resolveFragmentId(android.app.Fragment pFragment) {
        if (pFragment.getId() > 0) {
            return pFragment.getId();
        } else if (pFragment.getTag() != null && !TextUtils.isEmpty(pFragment.getTag())) {
            return pFragment.getTag();
        } else {
            return pFragment.getClass();
        }
    }

    /**
     * Same as the homonym method but for fragments from the compatiblity library.
     */
    protected Object resolveFragmentId(android.support.v4.app.Fragment pFragment) {
        if (pFragment.getId() > 0) {
            return pFragment.getId();
        } else if (pFragment.getTag() != null && !TextUtils.isEmpty(pFragment.getTag())) {
            return pFragment.getTag();
        } else {
            return pFragment.getClass();
        }
    }

    @Override
    public boolean keepResultOnHold(Task<?, ?, ?> pTask) {
        return false;
    }

    @Override
    public ExecutorService resolveExecutor(Task<?, ?, ?> pTask) {
        return mSerialExecutor;
    }

    @Override
    public boolean allowUnmanagedEmitters() {
        return true;
    }

    @Override
    public boolean allowInnerTasks() {
        return true;
    }

    @Override
    public boolean crashOnHandlerFailure() {
        return (mApplication.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
