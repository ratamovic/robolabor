package com.codexperiments.robolabor.test.common;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

public class TestApplication extends Application implements ApplicationContext.Provider {
	public static volatile TestApplication Instance;
	
	private ApplicationContext mApplicationContext;
//	private WeakReference<Activity> mCurrentActivity;
	private Activity mCurrentActivity;

	public TestApplication() {
		super();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Instance = this;
		mApplicationContext = new ApplicationContext(this);
	}
    
    @Override
    public ApplicationContext provideContext() {
        return mApplicationContext;
    }

    public void setApplicationContext(ApplicationContext pApplicationContext) {
        mApplicationContext = pApplicationContext;
    }

    public Activity getCurrentActivity() {
//        return mCurrentActivity.get();
        return mCurrentActivity;
    }

    public void setCurrentActivity(Activity pCurrentActivity) {
        Log.d(getClass().getSimpleName(), "setCurrentActivity " + pCurrentActivity);
//        mCurrentActivity = new WeakReference<Activity>(pCurrentActivity);
        mCurrentActivity = pCurrentActivity;
    }
}