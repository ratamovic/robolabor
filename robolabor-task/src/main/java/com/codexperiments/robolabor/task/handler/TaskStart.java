package com.codexperiments.robolabor.task.handler;

public interface TaskStart extends TaskCallback {
    void onStart(boolean pIsRestored);
}
