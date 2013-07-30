package com.codexperiments.robolabor.task.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.os.Process;

/**
 * TODO Comments
 */
public class AutoCleanMap<TKey, TValue> extends AbstractMap<TKey, TValue> {
    private ConcurrentHashMap<WeakKey<TKey>, WeakValue<TValue>> mMap;
    private ReferenceQueue<TKey> mQueue;

    public AutoCleanMap(int pCapacity) {
        mMap = new ConcurrentHashMap<WeakKey<TKey>, WeakValue<TValue>>(pCapacity);
        mQueue = new ReferenceQueue<TKey>();

        startCleanup();
    }

    public AutoCleanMap() {
        this(16);
    }

    private void startCleanup() {
        new Thread(new Runnable() {
            @SuppressWarnings("unchecked")
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
                while (true) {
                    try {
                        WeakKey<TKey> lWeakKey = (WeakKey<TKey>) mQueue.remove();
                        mMap.remove(lWeakKey);
                    } catch (InterruptedException eInterruptedException) {
                        // Ignore and retry.
                    }
                }
            }
        }).start();
    }

    @Override
    public TValue get(Object pKey) {
        // We cannot be sure pKey is a TKey. So use a WeakKey<Object> instead of WeakKey<TKey> and type erasure will do the rest.
        WeakValue<TValue> lWeakValue = mMap.get(new WeakKey<Object>(pKey));
        return (lWeakValue != null) ? lWeakValue.get() : null;
    }

    @Override
    public TValue put(TKey pKey, TValue pValue) {
        mMap.put(new WeakKey<TKey>(pKey, mQueue), new WeakValue<TValue>(pValue));
        return pValue;
    }

    @Override
    public Set<java.util.Map.Entry<TKey, TValue>> entrySet() {
        throw new UnsupportedOperationException();
    }

    private static class WeakKey<TKey> extends WeakReference<TKey> {
        private int mHashCode;

        public WeakKey(TKey pKey) {
            super(pKey, null);
            mHashCode = pKey.hashCode();
        }

        public WeakKey(TKey pKey, ReferenceQueue<TKey> pQueue) {
            super(pKey, pQueue);
            mHashCode = pKey.hashCode();
        }

        @Override
        public int hashCode() {
            return this.mHashCode;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object pOther) {
            if (this == pOther) return true;
            if (pOther == null) return false;
            if (getClass() != pOther.getClass()) return false;

            WeakKey<TKey> lOther = (WeakKey<TKey>) pOther;
            Object lValue = get();
            return (lValue != null) && (lValue == lOther.get());
        }
    }

    private static class WeakValue<TValue> extends WeakReference<TValue> {
        public WeakValue(TValue pValue) {
            super(pValue, null);
        }
    }
}
