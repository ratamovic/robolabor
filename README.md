robolabor
=========

Licensing
---------

Unknown yet... Just ask please!


robolabor-task
--------------

### Getting started

Robolabor Task is a module aiming at making asynchronous task management easier and less leak-prone on Android. It's still a work-in-progress-alpha version that need more thorough testing and some redesign but it works for a basic usage. This module works in a very similar way to AsyncTasks and can be used in the same contexts. But at the difference of Android AsyncTasks, it enables the use of inner-classes with references to an outer Activity, Fragment or any other kind of object without memory leaks.

To start an asynchrnous Task, simply:
- Create a global instance of AndroidTaskManager (using a singleton, a static variable, an Application class instance or whatever you want). Note that `keepResultOnHold()` option is enabled here to make sure termination handlers are called only when an Activity or Fragment is bound.

~~~
mTaskManager = new AndroidTaskManager(getApplication(), new AndroidTaskManagerConfig(getApplication()) {
    @Override
    public boolean keepResultOnHold(Task<?> pTask) {
        return true;
    }
});
~~~

- Retrieve the global instance in your Activity or Fragment
- Start managing the current Activity or Fragment
- If the Activity or Fragment stops at some point, stop managing the current Activity or Fragment, even if a task is still running.

~~~
public class MyActivity extends Activity {
    private Button mUIButton;
    private ProgressDialog mUIDialog;

    private TaskManager mTaskManager;
    private MyService mMyService;
    ...
    
    protected void onCreate(Bundle pBundle) {
        super.onCreate(pBundle);
        mUIDialog = new ProgressDialog(this);
        mUIButton = (Button) findViewById(R.id.button1);
        mUIButton.setOnClickListener(new OnClickListener() {
            public void onClick(View pView) {
                doMyStuff(new MyParam(...));
            }
        });
        
        mTaskManager = ...; // Get task manager from a singleton, static variable or Application class instance
        mMyService = ...
                        
    }

    public void onStart() {
        super.onStart();
        mTaskManager.manage(this);
        ...
    }

    public void onStop() {
        super.onStop();
        mUIDialog.dismiss();
        mTaskManager.unmanage(this);
        ...
    }
...
~~~

- While the Activity or Fragment is running, between a call to `TaskManager.manage()` and `TaskManager.unmanage()`, simply start a new asynchronous task using `TaskManager.execute()`. A helper class `TaskAdapter` is provided to implement only the methods you need:
    1. `TaskStart.onStart()` is called immediately.
    2. The task is run using `Task.onProcess()`
    3. When `Task.onProcess()` is over, `TaskResult.onFinish()` is called with the returned result or else `TaskResult.onFail()`if an exception was raised during processing.

~~~
...
    public void doMyStuff(final MyParam pMyParam) {
        mTaskManager.execute(new TaskAdapter<MyResult>() {
            MyService lMyService = mMyService;

            public void onStart(boolean pIsRestored) {
                mUIDialog = ProgressDialog.show(MyActivity.this, "Please wait...", "Doing my stuff...", true);
            }

            public MyResult onProcess(TaskNotifier pTaskNotifier) throws Exception {
                return lMyService.doMyStuff(pMyParam);
            }

            public void onFinish(MyResult pResult) {
                mUIDialog.dismiss();
            }

            public void onFail(Throwable pException) {
                mUIDialog.dismiss();
                Toast.makeText(MyActivity.this, "Oups!!! Something happened", Toast.LENGTH_LONG).show();
                pException.printStackTrace();
            }
        });
    }
}
~~~

**Note that:**
- **YOU CANNOT** access enclosing class variables while you are in the `Task.onProcess()` method. Use (possibly final) parameters or local variables instead.
- If the Activity or Fragment stops and a task is running, the callback will be automatically reattached to the new activity and `TaskStart.onStart()` executed again with pIsRestored set to true.
- Termination handlers can be called immediately when a task finishes even if no Activity or Fragment is bound when `keepResultOnHold()` option is set to false. In this situation, it is possible to know if outer object is bound by checking its reference (e.g. `MyActivity.this != null`).


### How it works

Robolabor task ensure no memory leaks is created during the asynchronous task processing by **dereferencing**, i.e. removing Java references, to enclosing class(es) by reflection. When a handler is called (i.e. `TaskStart.onStart()`, `TaskResult.onFinish()`, `TaskResult.onFail()`, `TaskProgress.onProgress()`), references to enclosing class(es) are restored temporarily while the handler is executed (and removed again right after). The trick here (you may like it or not) is to look for any field named like `this$0` in the passed task handlers. It is then easy to modify such references by reflection in Java.

Of course, in practice, this process is a bit more complicated, with some tricky edge cases to handle (such as tasks can container other tasks, etc.). In addition, since it relies on field naming, it is important to configure Proguard appropriately to avoid any surprise in a production application. But apart from that, I think this little library can bring back to inner-classes the flexibility they deserve.
