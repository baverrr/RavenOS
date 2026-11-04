package org.ravenos.home;

import android.app.Application;

/** Starts what RavenHome keeps running for the whole device, whichever part starts first. */
public class RavenApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SpeakerTuning.start(this);
        RemoteButtons.syncService(this);
    }
}
