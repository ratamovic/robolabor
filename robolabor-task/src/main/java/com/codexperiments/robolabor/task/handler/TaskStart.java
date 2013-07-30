package com.codexperiments.robolabor.task.handler;

public interface TaskStart extends TaskHandler {
    void onStart(boolean pIsRestored);
}
