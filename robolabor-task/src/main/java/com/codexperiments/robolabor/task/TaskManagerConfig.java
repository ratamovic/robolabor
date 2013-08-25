package com.codexperiments.robolabor.task;

import java.util.concurrent.ExecutorService;

import com.codexperiments.robolabor.task.handler.Task;

/**
 * Interface that defines how the TaskManager works.
 */
public interface TaskManagerConfig {
    /**
     * Gives an object identifying an emitter. Basicallt, this identifier is used later as an indirection to access the emitter
     * (actually a Weak reference to it) while avoiding any possible memory leak. This Id could be a String, an Integer
     * constant... Anything that identifies the emitter uniquely. For example:
     * <ul>
     * <li>if an Activity, like a Dashboard activity, is unique in the app, then we can use its class as an Id (i.e. one instance
     * at once, although activity can be recreated. But any task that is emitted by a HomeActivity can be bound to any later
     * instance of a HomeActivity).</li>
     * <li>If an activity, let's imagine a Web activity displaying a single specific web page, is used several times in the same
     * app, then it may be appropriate to use a more specific Id, such as page Url (e.g. for a task that loads the corresponding
     * page). We don't want an Activity to display the result of another.</li>
     * <ul>
     * 
     * @param pEmitter Emitter of a task the Id of which is needed.
     * @return Id of the emitter. Cannot be the emitter itself, or that could potentienally result in a memory leak.
     */
    Object resolveEmitterId(Object pEmitter);

    /**
     * Execution pipeline to use to run the task. Some tasks may use for example a "serial" executor, to ensure background tasks
     * are executed in order (like classic AsyncTasks starting from Android Gingerbread). Other tasks may need to be run in
     * parallel.
     * 
     * @param pTask Task that need to be executed on the executor.
     * @return Executor to use for the specified task.
     */
    ExecutorService resolveExecutor(Task<?, ?, ?> pTask);

    /**
     * Configuration option to indicate that TaskManager should wait for an object to be bound to the task before to execute task
     * termination handlers. For example, given an Activity that starts a task but get destroyed during processing, two cases may
     * occur:
     * <ul>
     * <li>keepResultOnHold is false: task termination handlers are executed as soon as the task is over, whether an activity is
     * bound to the task or not. If an activity is bound, onFinish and onFail are executed. If no activity is bound, then only
     * onResult is executed. is bound to the task</li>
     * <li>keepResultOnHold is true: task termination handlers (i.e. onFinish, onFail, ...) won't be called until an activity is
     * bound to the task (whether it is the emitting activity or an activity bound later through rebind()).</li>
     * </ul>
     * 
     * @param pTask Task the result of which need to be kept or not until an object is bound.
     * @return True to save task result until an object is bound or false to execute termination handlers immediately.
     */
    boolean keepResultOnHold(Task<?, ?, ?> pTask);

    /**
     * Configuration option to forbid use of unmanaged objects.
     * 
     * @return False if executing a task emitted by an unmanaged object should fail or true otherwise.
     */
    boolean allowUnmanagedEmitters();

    /**
     * Configuration option that allows task (not a task handler, i.e. TaskResult which can always be an inner class) declared as
     * an inner class to be executed by the TaskManager. Using inner tasks is more risky if you are unexperienced since
     * dereferencing is applied during processing (a NullPointerException is raised if you access the outer object members). This
     * problem doesn't exist with tasks based on static or normal classes since no outer class reference is used.
     * 
     * @return True to allow inner task to be executed and false otherwise.
     */
    boolean allowInnerTasks();

    /**
     * Configuration option that makes termination handlers crash the application if an uncaught runtime exception occur. Useful
     * for debugging purpose. Basically, a good strategy is to activate this option when debug mode is activated.
     * 
     * @return True to make application crash or false otherwise.
     */
    boolean crashOnHandlerFailure();
}