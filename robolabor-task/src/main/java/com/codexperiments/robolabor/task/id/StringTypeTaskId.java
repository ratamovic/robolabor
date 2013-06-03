package com.codexperiments.robolabor.task.id;

public class StringTypeTaskId implements TaskId
{
    private Class<?> mType;
    private String mId;

    public StringTypeTaskId(Class<?> pType, String pId)
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

        StringTypeTaskId lOther = (StringTypeTaskId) pOther;
        if (mId == null) {
            if (lOther.mId != null) return false;
        } else if (!mId.equals(lOther.mId)) return false;

        if (mType == null) return lOther.mType == null;
        else return mType.equals(lOther.mType);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mId == null) ? 0 : mId.hashCode());
        result = prime * result + ((mType == null) ? 0 : mType.hashCode());
        return result;
    }
}
