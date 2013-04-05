package com.codexperiments.robolabor.task;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

public class TaskManagerAndroid implements TaskManager
{
    private Handler mUIQueue;
    private Executor mTaskExecutor;
    //private Map<TaskHandler<?>, WeakReference<Object>> mTaskOwners;
    private Map<TaskHandler<?>, Class<?>> mTaskOwnerTypes;
    private Map<Class<?>, WeakReference<Object>> mTaskOwnersByType;


    /**
     * Must be instantiated from the UI Thread.
     */
    public TaskManagerAndroid() {
        super();
        // This code assumes the constructor is called on the UI Thread.
        mUIQueue = new Handler(Looper.getMainLooper());
        mTaskExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable pRunnable) {
                Thread thread = new Thread(pRunnable);
                thread.setDaemon(true); // TODO Check if that's necessary.
                return thread;
            }
        });
        //mTaskOwners = new HashMap<TaskHandler<?>, WeakReference<Object>>();
        mTaskOwnerTypes = new HashMap<TaskHandler<?>, Class<?>>();
        mTaskOwnersByType = new HashMap<Class<?>, WeakReference<Object>>();
    }
    
    public void manage(Activity pActivity) {
        mTaskOwnersByType.put(pActivity.getClass(), new WeakReference<Object>(pActivity));
    }

    public void unmanage(Activity pActivity) {
        WeakReference<Object> lWeakRef = mTaskOwnersByType.get(pActivity.getClass());
        if (lWeakRef != null) lWeakRef.clear();
    }

    @Override
    public <TResult> void execute(final TaskHandler<TResult> pHandler) {
        saveOuterRef(pHandler);
        mTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final TResult result = pHandler.onProcess();
                    mUIQueue.post(new Runnable() {
                        @Override
                        public void run() {
                            restoreOuterRef(pHandler);
                            pHandler.onFinish(result);
                        }
                    });
                } catch (final Exception eException) {
                    mUIQueue.post(new Runnable() {
                        @Override
                        public void run() {
                            pHandler.onError(eException);
                        }
                    });
                }
            }
        });
    }
    
    private <TResult> void saveOuterRef(TaskHandler<TResult> pHandler) {
        // Dereference the outer class to avoid any conflict.
        try {
            Field lOuterRefField = findOuterRefField(pHandler);
         // TODO if null if isinnerclass
            //mTaskOwners.put(pHandler, new WeakReference<Object>(outerRefField.get(pHandler)));
            Object lOuterRef = lOuterRefField.get(pHandler);
            mTaskOwnerTypes.put(pHandler, lOuterRef.getClass());
            mTaskOwnersByType.put(lOuterRef.getClass(), new WeakReference<Object>(lOuterRefField.get(pHandler)));
            lOuterRefField.set(pHandler, null);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private <TResult> void restoreOuterRef(TaskHandler<TResult> pHandler) {
        // Dereference the outer class to avoid any conflict.
        Class<?> outerRefType = mTaskOwnerTypes.get(pHandler);
        WeakReference<Object> lWeakOuterRef = mTaskOwnersByType.get(outerRefType);
        try {
            Object lOuterRef = lWeakOuterRef.get();
            // TODO if null
            Field lOuterRefField = findOuterRefField(pHandler);
            lOuterRefField.setAccessible(true);
            lOuterRefField.set(pHandler, lOuterRef);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private Field findOuterRefField(Object pHandler) {
        Field[] lFields = pHandler.getClass().getDeclaredFields();
        for (Field lField : lFields) {
            String lFieldName = lField.getName();
            if (lFieldName.startsWith("this$")) {
                lField.setAccessible(true);
                return lField;
            }
        }
        return null;
    }

    @Override
    public void notifyProgress(final TaskProgressNotifier pProgressHandler) {
        mUIQueue.post(new Runnable() {
            @Override
            public void run() {
                pProgressHandler.onProgress();
            }
        });
    }
}
