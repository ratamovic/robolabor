package com.codexperiments.robolabor.test.common;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;

public class TestCase<TActivity extends Activity> extends ActivityInstrumentationTestCase2<TActivity> {
    protected TestApplication mApplication;
    protected ApplicationContext mApplicationContext;

	public TestCase(Class<TActivity> activityClass) {
        super(activityClass);
    }

    @Override
	protected void setUp() throws Exception {
		super.setUp();

		// Patch to synchronize Application and Test initialization, as Application initialization occurs
		// on the main thread whereas Test initialization occurs on the Instrumentation thread...
		while (TestApplication.Instance == null) {
			getInstrumentation().runOnMainSync(new Runnable() {
				public void run() {
					// No op.
				}
			});
		}
		
		mApplication = TestApplication.Instance;
        mApplicationContext = new ApplicationContext(mApplication);
        mApplication.setApplicationContext(mApplicationContext);

        // Execute initialization code on UI Thread.
        final List<Exception> throwableHolder = new ArrayList<Exception>(1);
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                try {
                    setUpOnUIThread();
                } catch (Exception eException) {
                    throwableHolder.add(eException);
                }
            }
        });
        // If an exception occurred during UI Thread initialization, re-throw the exception.
        if (throwableHolder.size() > 0) throw throwableHolder.get(1);
	}

    protected void setUpOnUIThread() throws Exception {
    }

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		TestApplication.Instance.setCurrentActivity(null);
	}
    
    @SuppressWarnings("unchecked")
    protected TActivity getCurrentActivity() {
        
        return (TActivity) TestApplication.Instance.getCurrentActivity();
    }
}