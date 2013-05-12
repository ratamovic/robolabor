package com.codexperiments.robolabor.test.common;

import android.app.Activity;
import android.app.Application;

public class TestApplication extends Application implements TestApplicationContext.Provider
{
    private static volatile TestApplication sInstance;

    private TestApplicationContext mApplicationContext;
    private Activity mCurrentActivity;

    public static TestApplication getInstance(final TestCase<?> pTestCase)
    {
        // Patch to synchronize Application and Test initialization, as Application initialization occurs
        // on the main thread whereas Test initialization occurs on the Instrumentation thread...
        while (TestApplication.sInstance == null) {
            pTestCase.getInstrumentation().runOnMainSync(new Runnable() {
                public void run()
                {
                    // No op.
                }
            });
        }
        return TestApplication.sInstance;
    }

    public TestApplication()
    {
        super();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mApplicationContext = new TestApplicationContext(this);
        sInstance = this;
    }

    @Override
    public TestApplicationContext provideContext()
    {
        return mApplicationContext;
    }

    protected Activity getCurrentActivity()
    {
        return mCurrentActivity;
    }

    protected void setCurrentActivity(Activity pCurrentActivity)
    {
        mCurrentActivity = pCurrentActivity;
    }
}
