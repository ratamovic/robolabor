package com.codexperiments.robolabor.task;

import com.codexperiments.robolabor.task.android.AndroidTaskManager.TaskPipeBuilder;
import com.codexperiments.robolabor.task.handler.Task;
import com.codexperiments.robolabor.task.handler.TaskResult;

/**
 * Terminology:
 * 
 * <ul>
 * <li>Emitter: A task emitter is, in Java terms, an outer class object that requests a task to execute. Thus, a task can have
 * emitters only if it is an inner, local or anonymous class. It's important to note that an object can have one or several
 * emitters since this is allowed by the Java language (an inner class can keep reference to several enclosing class).</li>
 * <li>Dereferencing: An inner class task keeps references to its emitters. These references must be removed temporarily during
 * processing to avoid possible memory leaks (e.g. if a task references an activity that gets destroyed during processing).</li>
 * <li>Referencing: References to emitters must be restored to execute task handlers (onFinish(), onFail(), onProgress()) or else,
 * the task would be unable to communicate with the outside world since it has be dereferenced. Referencing is possible only if
 * all the necessary emitters, managed by the TaskManager, are still reachable. If not, task handlers cannot be executed until all
 * are reachable (and if configuration requires to keep results on hold).</li>
 * </ul>
 * 
 * <b>The problem:</b>
 * 
 * There are many ways to handle asynchronous tasks in Android to load data or perform some background processing. Several ways to
 * handle this exist, among which:
 * <ul>
 * <li>AsyncTasks: One of the most efficient ways to write asynchronous tasks but also to make mistakes. Can be used as such
 * mainly for short-lived tasks (or by using WeakReferences).</li>
 * <li>Services or IntentServices (with Receivers): Probably the most flexible and safest way to handle asynchronous tasks, but
 * requires some boilerplate "plumbing"</li>
 * <li>Loaders: which are tied to the activity life-cycle and also a bit difficult to write when handling all the specific cases
 * that may occur (Loader reseted, etc.). They require less plumbing but still some.</li>
 * <li>Content Providers: which are just nice to use to create a remote data source. Cumbersome and annoying to write for any
 * other use... And they are not inherently threaded anyway.</li>
 * <li>...</li>
 * </ul>
 * Each technique has its drawbacks. The most practical way, AsyncTasks, can easily cause memory leaks which occur especially with
 * inner classes which keep a reference to the outer object. A typical example is an Activity referenced from an inner AsyncTask:
 * when the Activity is destroyed because of a configuration change (e.g. screen rotation) or because user leave the Activity
 * (e.g. with Home button), then the executing AsyncTask still references its containing Activity which cannot be garbage
 * collected. Even worse, accessing the emitting Activity after AsyncTask is over may cause either no result at all or exceptions,
 * because a new version of the Activity may have been created in-between and the older one is not displayed any more or has freed
 * some resources.
 * 
 * <b>How it works:</b>
 * 
 * As soon as a task is enqueued in execute(), all its emitters are dereferenced to avoid any possible memory leaks during
 * processing (in Task.onProcess()). In other words, any emitters (i.e. outer class references) are replaced with null. This means
 * that your Task:
 * <ul>
 * <li>Can execute safely without memory leaks. Activity or any other emitter can still be garbage collected.</li>
 * <li><b>CANNOT access outer emitters (again, any outer class reference) from the onProcess method() or must use a static
 * Task!</b> That's price to pay for this memory safety... Use allowInnerTasks() in Configuration object to forbid the use of
 * inner tasks.</li>
 * <li><b>Any member variables need by a Task must be copied in Task constructor.</b> That way, the Task can work safely in a
 * closed environment without the interference of other threads. Indeed, don't share any variable between onProcess() and any
 * other threads, UI-Thread included, as this could lead to unpredictable result (because of Thread caching or instruction
 * reordering) unless some synchronization is performed (which can lead to bottleneck or a dead lock in extreme case if not
 * appropriately handled).</li>
 * </ul>
 * 
 * Before, during or after processing, several handlers (i.e. callbacks) can be called:
 * <ul>
 * <li>onStart()</li>
 * <li>onProgress()</li>
 * <li>onFinish()</li>
 * <li>onFail()</li>
 * </ul>
 * Right before and after these handlers are invoked, emitters are respectively referenced and dereferenced to allow accessing the
 * outer class. If outer class is not available (e.g. if Activity has been destroyed but not recreated yet).
 */
public interface TaskManager {
    void manage(Object pEmitter);

    void unmanage(Object pEmitter);

    <TResult> TaskPipeBuilder<TResult> when(Task<?, ?, TResult> pTask);

    <TResult> TaskPipeBuilder<TResult> when(Task<?, ?, TResult> pTask, TaskResult<TResult> pTaskResult);

    <TParam, TProgress, TResult> TaskRef<TResult> execute(Task<TParam, TProgress, TResult> pTask);

    <TParam, TProgress, TResult> TaskRef<TResult> execute(Task<TParam, TProgress, TResult> pTask, TaskResult<TResult> pTaskResult);

    <TParam, TProgress, TResult> boolean rebind(TaskRef<TResult> pTaskRef, TaskResult<TResult> pTaskResult);

    void notifyProgress(/* TaskProgress pProgress */);
}
