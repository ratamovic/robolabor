package com.codexperiments.robolabor.test.common;

import android.app.Activity;

public class TestException extends RuntimeException
{
    private static final long serialVersionUID = -4615749565432900659L;

    protected TestException(String pMessage, Object... pArguments)
    {
        super(String.format(pMessage, pArguments));
    }

    protected TestException(Throwable pThrowable, String pMessage, Object... pArguments)
    {
        super(String.format(pMessage, pArguments), pThrowable);
    }

    public static TestException wrongActivity(Activity pActivity)
    {
        return new TestException("Wrong Activity type (%1$s). This may happen if tested activity navigated to another one.",
                                 pActivity.getClass());
    }

    public static TestException unknownManager(Class<?> pManagerClass)
    {
        return new TestException("%1$s is not a registered service.", pManagerClass.getName());
    }
}
