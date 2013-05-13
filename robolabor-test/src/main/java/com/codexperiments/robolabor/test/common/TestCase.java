package com.codexperiments.robolabor.test.common;

import static java.lang.Thread.sleep;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.test.ActivityInstrumentationTestCase2;

public class TestCase<TActivity extends Activity> extends ActivityInstrumentationTestCase2<TActivity>
{
    private Class<?> mActivityClass;
    private TestApplication mApplication;
    private TestApplicationContext mApplicationContext;

    public TestCase(Class<TActivity> pActivityClass)
    {
        super(pActivityClass);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        mApplication = TestApplication.getInstance(this);
        mApplicationContext = mApplication.provideContext();

        // Execute initialization code on UI Thread.
        final List<Exception> lThrowableHolder = new ArrayList<Exception>(1);
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run()
            {
                try {
                    setUpOnUIThread();
                } catch (Exception eException) {
                    lThrowableHolder.add(eException);
                }
            }
        });
        // If an exception occurred during UI Thread initialization, re-throw the exception.
        if (lThrowableHolder.size() > 0) {
            throw lThrowableHolder.get(0);
        }
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        mApplication.setCurrentActivity(null);
        mApplicationContext.removeManagers();
    }

    @Override
    @SuppressWarnings("unchecked")
    public TActivity getActivity()
    {
        Activity lActivity = mApplication.getCurrentActivity();
        if (lActivity == null) {
            TActivity lNewActivity = super.getActivity();
            mActivityClass = lNewActivity.getClass();
            mApplication.setCurrentActivity(lNewActivity);
            return lNewActivity;
        } else {
            if (mActivityClass.isInstance(lActivity)) {
                return (TActivity) lActivity;
            } else {
                throw TestException.wrongActivity(lActivity);
            }
        }
    }

    public TActivity getActivity(Intent pIntent)
    {
        setActivityIntent(pIntent);
        return getActivity();
    }

    @SuppressWarnings("unchecked")
    public <TOtherActivity extends Activity> TOtherActivity getOtherActivity(Class<TOtherActivity> pOtherActivityClass)
    {
        return (TOtherActivity) mApplication.getCurrentActivity();
    }

    protected void setUpOnUIThread() throws Exception
    {
    }

    protected void rotateActivitySeveralTimes(int pCount) throws InterruptedException
    {
        for (int i = 0; i < pCount; ++i) {
            // Wait some time before turning.
            sleep(500);

            Resources lResources = getInstrumentation().getTargetContext().getResources();
            Configuration lConfiguration = lResources.getConfiguration();
            if (lConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mApplication.getCurrentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                mApplication.getCurrentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
    }

    protected TActivity terminateActivity(TActivity pActivity) throws InterruptedException
    {
        pActivity.finish();
        setActivity(null);
        mApplication.setCurrentActivity(null);
        return null;
    }

    protected void garbageCollect() throws InterruptedException
    {
        for (int i = 0; i < 3; ++i) {
            System.gc();
            getInstrumentation().runOnMainSync(new Runnable() {
                public void run()
                {
                    System.gc();
                }
            });
        }
    }

    public TestApplication getApplication()
    {
        return mApplication;
    }

    public TestApplicationContext getApplicationContext()
    {
        return mApplicationContext;
    }
}
