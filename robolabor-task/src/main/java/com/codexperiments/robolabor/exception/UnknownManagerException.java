package com.codexperiments.robolabor.exception;

public class UnknownManagerException extends RuntimeException
{
    private static final long serialVersionUID = 7075896587022439079L;


    public UnknownManagerException(String pMessage, Object... pArguments) {
        super(String.format(pMessage, pArguments));
    }
}
