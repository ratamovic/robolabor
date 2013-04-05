package com.codexperiments.robolabor.task;

public interface TaskHandlerWithProgress<TResult> extends TaskHandler<TResult>, TaskProgressNotifier
{}
