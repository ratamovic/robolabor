package com.codexperiments.robolabor.task.id;

public class IntTaskId implements TaskId
{
    private int mId;

    public IntTaskId(int pId)
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

        IntTaskId lOther = (IntTaskId) pOther;
        return mId == lOther.mId;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + mId;
        return result;
    }
}
