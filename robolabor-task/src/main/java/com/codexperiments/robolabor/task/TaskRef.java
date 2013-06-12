package com.codexperiments.robolabor.task;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * TaskRef identifies uniquely one instance of a task. A task reference, as opposed to a task identity, is a bit equivalent to an
 * object reference in standard Java, as opposed to an Object identity (i.e. two tasks with the same Id can have different
 * references if they have been instantiated separately). The main purpose of a TaskRef is to maintain reference value, even if it
 * is serialized, in order to allow any object to rebind() to an existing task. More specifically, if you pass a TaskRef between
 * activities, it will still reference the same task. Its hidden purpose is to maintain TResult generic parameter between a call
 * to execute() and listen(). Without it, user could rebind a task handler that does not provide the same type.
 * 
 * @param <TResult> Type of the result returned by the corresponding task.
 */
public class TaskRef<TResult> implements Parcelable, Serializable
{
    private static final long serialVersionUID = 2997786361152719033L;

    private int mId;

    public TaskRef(int pId)
    {
        super();
        mId = pId;
    }

    protected TaskRef(Parcel pParcel)
    {
        mId = pParcel.readInt();
    }

    @Override
    public void writeToParcel(Parcel pParcel, int pFlags)
    {
        pParcel.writeInt(mId);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public boolean equals(Object pOther)
    {
        if (this == pOther) return true;
        if (pOther == null) return false;
        if (getClass() != pOther.getClass()) return false;

        TaskRef<?> lOther = (TaskRef<?>) pOther;
        return mId == lOther.mId;
    }

    @Override
    public int hashCode()
    {
        return mId;
    }

    public static final Parcelable.Creator<TaskRef<?>> CREATOR = new Parcelable.Creator<TaskRef<?>>() {
        public TaskRef<?> createFromParcel(Parcel pParcel)
        {
            return new TaskRef<Object>(pParcel);
        }

        public TaskRef<?>[] newArray(int pSize)
        {
            return new TaskRef[pSize];
        }
    };
}
