package com.codexperiments.robolabor.task.id;

public class TypeTaskId implements TaskId
{
    private Class<?> mId;

    public TypeTaskId(Class<?> pId)
    {
        super();
        mId = pId;
    }

    @Override
    public boolean equals(Object pOther)
    {
        if (this == pOther) return true;
        if (pOther == null) return false;
        if (getClass() != pOther.getClass()) return false;

        TypeTaskId lOther = (TypeTaskId) pOther;
        if (mId == null) return lOther.mId == null;
        else return mId.equals(lOther.mId);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mId == null) ? 0 : mId.hashCode());
        return result;
    }
}
