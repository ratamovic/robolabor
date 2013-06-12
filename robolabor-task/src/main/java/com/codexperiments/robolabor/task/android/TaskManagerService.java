package com.codexperiments.robolabor.task.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class TaskManagerService extends Service
{
    @Override
    public int onStartCommand(Intent pIntent, int pFlags, int pStartId)
    {
        // We cannot (yet) restore executing tasks...
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent pIntent)
    {
        // No binding allowed nor needed.
        return null;
    }
}
