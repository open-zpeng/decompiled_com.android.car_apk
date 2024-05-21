package com.android.settingslib.suggestions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.service.settings.suggestions.ISuggestionService;
import android.service.settings.suggestions.Suggestion;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.util.List;
/* loaded from: classes3.dex */
public class SuggestionController {
    private static final boolean DEBUG = false;
    private static final String TAG = "SuggestionController";
    private ServiceConnectionListener mConnectionListener;
    private final Context mContext;
    private ISuggestionService mRemoteService;
    private ServiceConnection mServiceConnection = createServiceConnection();
    private final Intent mServiceIntent;

    /* loaded from: classes3.dex */
    public interface ServiceConnectionListener {
        void onServiceConnected();

        void onServiceDisconnected();
    }

    public SuggestionController(Context context, ComponentName service, ServiceConnectionListener listener) {
        this.mContext = context.getApplicationContext();
        this.mConnectionListener = listener;
        this.mServiceIntent = new Intent().setComponent(service);
    }

    public void start() {
        this.mContext.bindServiceAsUser(this.mServiceIntent, this.mServiceConnection, 1, Process.myUserHandle());
    }

    public void stop() {
        if (this.mRemoteService != null) {
            this.mRemoteService = null;
            this.mContext.unbindService(this.mServiceConnection);
        }
    }

    @Nullable
    @WorkerThread
    public List<Suggestion> getSuggestions() {
        if (isReady()) {
            try {
                return this.mRemoteService.getSuggestions();
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Error when calling getSuggestion()", e);
                return null;
            } catch (NullPointerException e2) {
                Log.w(TAG, "mRemote service detached before able to query", e2);
                return null;
            }
        }
        return null;
    }

    public void dismissSuggestions(Suggestion suggestion) {
        if (!isReady()) {
            Log.w(TAG, "SuggestionController not ready, cannot dismiss " + suggestion.getId());
            return;
        }
        try {
            this.mRemoteService.dismissSuggestion(suggestion);
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Error when calling dismissSuggestion()", e);
        }
    }

    public void launchSuggestion(Suggestion suggestion) {
        if (!isReady()) {
            Log.w(TAG, "SuggestionController not ready, cannot launch " + suggestion.getId());
            return;
        }
        try {
            this.mRemoteService.launchSuggestion(suggestion);
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Error when calling launchSuggestion()", e);
        }
    }

    private boolean isReady() {
        return this.mRemoteService != null;
    }

    private ServiceConnection createServiceConnection() {
        return new ServiceConnection() { // from class: com.android.settingslib.suggestions.SuggestionController.1
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName name, IBinder service) {
                SuggestionController.this.mRemoteService = ISuggestionService.Stub.asInterface(service);
                if (SuggestionController.this.mConnectionListener != null) {
                    SuggestionController.this.mConnectionListener.onServiceConnected();
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName name) {
                if (SuggestionController.this.mConnectionListener != null) {
                    SuggestionController.this.mRemoteService = null;
                    SuggestionController.this.mConnectionListener.onServiceDisconnected();
                }
            }
        };
    }
}
