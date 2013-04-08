package com.codexperiments.robolabor.task.context;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.codexperiments.robolabor.task.Task;
import com.codexperiments.robolabor.task.TaskContext;
import com.codexperiments.robolabor.task.TaskHelper;

public class ActivityTaskContext implements TaskContext
{
    private Map<Task<?>, Class<?>> mTaskOwnerTypes;
    private Map<Class<?>, WeakReference<Object>> mTaskOwnersByType;


    public ActivityTaskContext() {
        super();
        mTaskOwnerTypes = new HashMap<Task<?>, Class<?>>();
        mTaskOwnersByType = new HashMap<Class<?>, WeakReference<Object>>();
    }

    public void manage(Object pOwner) {
        mTaskOwnersByType.put(pOwner.getClass(), new WeakReference<Object>(pOwner));
    }

    public void unmanage(Object pOwner) {
        WeakReference<Object> lWeakRef = mTaskOwnersByType.get(pOwner.getClass());
        if ((lWeakRef != null) && (lWeakRef.get() == pOwner)) {
            lWeakRef.clear();
        }
    }

    @Override
    public boolean unmap(Task<?> pTask) {
        Object lOuter = TaskHelper.saveOuterRef(pTask);
        if (lOuter == null) return false;
        
        Class<?> lOuterType = lOuter.getClass();
        mTaskOwnerTypes.put(pTask, lOuterType);
        mTaskOwnersByType.put(lOuterType, new WeakReference<Object>(lOuter));
        return true;
    }

    @Override
    public boolean map(Task<?> pTask) {
        Class<?> lOuterType = mTaskOwnerTypes.get(pTask);
        WeakReference<Object> lOuterRef = mTaskOwnersByType.get(lOuterType);
        
        return TaskHelper.restoreOuterRef(pTask, lOuterRef.get());
    }
}
