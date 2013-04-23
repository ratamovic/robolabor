package com.codexperiments.robolabor.test.common;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Application;
import android.support.v4.app.Fragment;

public class TestApplicationContext {
    private List<Object> mManagers;
	private int mStartCounter;
	//private Application mApplication;

    public static TestApplicationContext from(Application pApplication) {
    	if ((pApplication != null) && (pApplication instanceof Provider)) {
        	return ((Provider) pApplication).provideContext();
    	}
    	throw TestException.invalidConfiguration("Could not retrieve application context from Fragment");
    }

    public static TestApplicationContext from(Activity pActivity) {
    	if (pActivity != null) {
        	Application application = pActivity.getApplication();
        	if ((application != null) && (application instanceof Provider)) {
            	return ((Provider) application).provideContext();
        	}
    	}
    	throw TestException.invalidConfiguration("Could not retrieve application context from Activity");
    }

    public static TestApplicationContext from(Fragment pFragment) {
        if (pFragment != null) {
            Activity lActivity = pFragment.getActivity();
            if (lActivity != null) {
                Application lApplication = lActivity.getApplication();
                if ((lApplication != null) && (lApplication instanceof Provider)) {
                    return ((Provider) lApplication).provideContext();
                }
            }
        }
        throw TestException.invalidConfiguration("Could not retrieve application context from Activity");
    }

    public static TestApplicationContext from(android.app.Service pService) {
    	if (pService != null) {
        	Application application = pService.getApplication();
        	if ((application != null) && (application instanceof Provider)) {
            	return ((Provider) application).provideContext();
        	}
    	}
    	throw TestException.invalidConfiguration("Could not retrieve application context from Application");
    }

    public TestApplicationContext(Application pApplication) {
        super();
//        mApplication = pApplication;
        mManagers = new ArrayList<Object>(20);
        mStartCounter = 0;
    }
    
    public void start() {
    	// If another activity is launched, start() of the 2nd activity will be called before the stop() of the 1st.
    	// Hence we need a counter to ensure we restart() services properly in this case. This was especially
    	// problematic because EventBus listeners were removed right after they were added...
    	if (mStartCounter > 0) {
    		//doStop();
    	}
    	//doStart();
    	++mStartCounter;
    }

    public void stop() {
    	--mStartCounter;
    	if (mStartCounter == 0) {
    		//doStop();
    	}
    }

    public void registerManager(Object pManager) {
        mManagers.add(pManager);
    }

    public void removeManager(Class<?> pManagerType) {
        for (Object iManager : mManagers) {
            if (pManagerType.isInstance(iManager)) {
                mManagers.remove(iManager);
            }
        }
    }

    public void removeManagers() {
        mManagers.clear();
    }

    @SuppressWarnings("unchecked")
    public <TManager> TManager getManager(Class<TManager> pManagerClass) {
        for (Object iManager : mManagers) {
            if (pManagerClass.isInstance(iManager)) {
                return (TManager) iManager;
            }
        }
        throw TestException.unknownManager(pManagerClass);
    }

    public interface Provider {
        TestApplicationContext provideContext();
    }
}
