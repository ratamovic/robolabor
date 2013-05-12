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
    protected TestApplication mApplication;
    protected TestApplicationContext mApplicationContext;

    public TestCase(Class<TActivity> activityClass)
    {
        super(activityClass);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        // Patch to synchronize Application and Test initialization, as Application initialization occurs
        // on the main thread whereas Test initialization occurs on the Instrumentation thread...
        while (TestApplication.Instance == null) {
            getInstrumentation().runOnMainSync(new Runnable() {
                public void run()
                {
                    // No op.
                }
            });
        }

        mApplication = TestApplication.Instance;
        mApplicationContext = new TestApplicationContext(mApplication);
        mApplication.setApplicationContext(mApplicationContext);

        // Execute initialization code on UI Thread.
        final List<Exception> throwableHolder = new ArrayList<Exception>(1);
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run()
            {
                try {
                    setUpOnUIThread();
                } catch (Exception eException) {
                    throwableHolder.add(eException);
                }
            }
        });
        // If an exception occurred during UI Thread initialization, re-throw the exception.
        if (throwableHolder.size() > 0) {
            throw throwableHolder.get(1);
        }
    }

    public TActivity getActivity(Intent pIntent)
    {
        setActivityIntent(pIntent);
        return super.getActivity();
    }

    protected void setUpOnUIThread() throws Exception
    {
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        TestApplication.Instance.setCurrentActivity(null);
    }

    @SuppressWarnings("unchecked")
    protected TActivity getCurrentActivity()
    {
        return (TActivity) TestApplication.Instance.getCurrentActivity();
    }

    protected void recreateActivitySeveralTimes(int pCount) throws InterruptedException
    {
        Activity lActivity = getActivity();
        for (int i = 0; i < pCount; ++i) {
            // Wait some time before turning.
            sleep(500);

            Resources lResources = getInstrumentation().getTargetContext().getResources();
            Configuration lConfiguration = lResources.getConfiguration();
            if (lConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                lActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                lActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
    }

    protected TActivity terminateActivity(TActivity pActivity) throws InterruptedException
    {
        TActivity lActivity = pActivity;
        lActivity.finish();
        setActivity(null);
        TestApplication.Instance.setCurrentActivity(null);
        return null;
    }
}
