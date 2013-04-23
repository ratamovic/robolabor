package com.codexperiments.robolabor.test.common;


/**
 * Indicates a case that should never happen and which indicates a programming or configuration error (e.g. a reflection call
 * which fails), a default case that should never happen in a switch, etc.
 */
public class TestException extends RuntimeException
{
    private static final long serialVersionUID = -4615749565432900659L;


    private TestException(String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments));
    }

    private TestException(Throwable pThrowable, String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments), pThrowable);
    }


    /**
     * Indicates that an unexpected switch case or if/else case has occured.
     */
    public static TestException illegalCase() {
        return new TestException("Illegal case");
    }

    /**
     * Indicates that configuration is invalid.
     */
    public static TestException invalidConfiguration(String pMessage, Object... pArguments) {
        return new TestException(pMessage, pArguments);
    }

    /**
     * Use this method when configuration is invalid.
     */
    public static TestException unknownManager(Class<?> pManagerClass) {
        return new TestException("%1$s is not a registered service.", pManagerClass.getName());
    }
}
