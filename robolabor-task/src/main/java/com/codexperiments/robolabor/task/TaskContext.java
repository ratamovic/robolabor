package com.codexperiments.robolabor.task;

import java.lang.reflect.Field;

import com.codexperiments.robolabor.exception.InternalException;

public interface TaskContext
{
    boolean unmap(Task<?> pTask);

    boolean map(Task<?> pTask);

    public static class Helper
    {
        public static Object saveOuterRef(Object pHandler) {
            // Dereference the outer class to avoid any conflict.
            try {
                Field lOuterRefField = findOuterRefField(pHandler);
             // TODO if null if isinnerclass
                Object lOuterRef = lOuterRefField.get(pHandler);
                lOuterRefField.set(pHandler, null);
                return lOuterRef;
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw InternalException.illegalCase();
            } catch (IllegalAccessException eIllegalAccessException) {
                throw InternalException.illegalCase();
            }
        }

        public static boolean restoreOuterRef(Object pObject, Object pOuterRef) {
            // Dereference the outer class to avoid any conflict.
            try {
                if (pOuterRef == null) return false;
                
                Field lOuterRefField = findOuterRefField(pObject);
                lOuterRefField.setAccessible(true);
                lOuterRefField.set(pObject, pOuterRef);
                return true;
            } catch (IllegalArgumentException eIllegalArgumentException) {
                throw InternalException.illegalCase();
            } catch (IllegalAccessException eIllegalAccessException) {
                throw InternalException.illegalCase();
            }
        }

        private static Field findOuterRefField(Object pHandler) {
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
    }
}
