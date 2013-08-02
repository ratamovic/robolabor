package com.codexperiments.robolabor.task.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class EmptyLock implements Lock {
    @Override
    public void lock() {
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        return true;
    }

    @Override
    public boolean tryLock(long pTime, TimeUnit pUnit) throws InterruptedException {
        return true;
    }

    @Override
    public void unlock() {
    }
}