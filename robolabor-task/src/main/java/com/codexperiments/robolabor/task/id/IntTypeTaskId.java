package com.codexperiments.robolabor.task.id;

public class IntTypeTaskId implements TaskId
{
    private Class<?> mType;
    private int mId;

    public IntTypeTaskId(Class<?> pType, int pId)
    {
        super();
        mType = pType;
        mId = pId;
    }

    @Override
    public boolean equals(Object pOther)
    {
        if (this == pOther) return true;
        if (pOther == null) return false;
        if (getClass() != pOther.getClass()) return false;

        IntTypeTaskId lOther = (IntTypeTaskId) pOther;
        if (mId != lOther.mId) return false;
        if (mType == null) return lOther.mType == null;
        else return mType.equals(lOther.mType);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + mId;
        result = prime * result + ((mType == null) ? 0 : mType.hashCode());
        return result;
    }
}
