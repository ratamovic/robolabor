package com.codexperiments.robolabor.task.id;


public class IntegerTaskId implements TaskId
{
    private Integer mId;

    public IntegerTaskId(Integer pId)
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

        IntegerTaskId lOther = (IntegerTaskId) pOther;
        if (mId == null) return lOther.mId == null;
        else return mId.equals(lOther.mId);
    }

    @Override
    public int hashCode()
    {
        return (mId == null) ? 0 : mId.hashCode();
    }
}
