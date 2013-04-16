package com.codexperiments.robolabor.task;

import java.util.concurrent.ExecutorService;

public interface TaskConfiguration {
    ExecutorService getExecutor();

    boolean keepResultOnHold();
}
