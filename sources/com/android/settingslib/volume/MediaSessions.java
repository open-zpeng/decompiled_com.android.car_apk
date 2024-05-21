package com.android.settingslib.volume;

import android.app.PendingIntent;
import android.car.encryptionrunner.DummyEncryptionRunner;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.IRemoteVolumeController;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
/* loaded from: classes3.dex */
public class MediaSessions {
    private static final String TAG = Util.logTag(MediaSessions.class);
    private static final boolean USE_SERVICE_LABEL = false;
    private final Callbacks mCallbacks;
    private final Context mContext;
    private final H mHandler;
    private boolean mInit;
    private final MediaSessionManager mMgr;
    private final Map<MediaSession.Token, MediaControllerRecord> mRecords = new HashMap();
    private final MediaSessionManager.OnActiveSessionsChangedListener mSessionsListener = new MediaSessionManager.OnActiveSessionsChangedListener() { // from class: com.android.settingslib.volume.MediaSessions.1
        @Override // android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            MediaSessions.this.onActiveSessionsUpdatedH(controllers);
        }
    };
    private final IRemoteVolumeController mRvc = new IRemoteVolumeController.Stub() { // from class: com.android.settingslib.volume.MediaSessions.2
        public void remoteVolumeChanged(MediaSession.Token sessionToken, int flags) throws RemoteException {
            MediaSessions.this.mHandler.obtainMessage(2, flags, 0, sessionToken).sendToTarget();
        }

        public void updateRemoteController(MediaSession.Token sessionToken) throws RemoteException {
            MediaSessions.this.mHandler.obtainMessage(3, sessionToken).sendToTarget();
        }
    };

    /* loaded from: classes3.dex */
    public interface Callbacks {
        void onRemoteRemoved(MediaSession.Token token);

        void onRemoteUpdate(MediaSession.Token token, String str, MediaController.PlaybackInfo playbackInfo);

        void onRemoteVolumeChanged(MediaSession.Token token, int i);
    }

    public MediaSessions(Context context, Looper looper, Callbacks callbacks) {
        this.mContext = context;
        this.mHandler = new H(looper);
        this.mMgr = (MediaSessionManager) context.getSystemService("media_session");
        this.mCallbacks = callbacks;
    }

    public void dump(PrintWriter writer) {
        writer.println(getClass().getSimpleName() + " state:");
        writer.print("  mInit: ");
        writer.println(this.mInit);
        writer.print("  mRecords.size: ");
        writer.println(this.mRecords.size());
        int i = 0;
        for (MediaControllerRecord r : this.mRecords.values()) {
            i++;
            dump(i, writer, r.controller);
        }
    }

    public void init() {
        if (D.BUG) {
            Log.d(TAG, DummyEncryptionRunner.INIT);
        }
        this.mMgr.addOnActiveSessionsChangedListener(this.mSessionsListener, null, this.mHandler);
        this.mInit = true;
        postUpdateSessions();
        this.mMgr.registerRemoteVolumeController(this.mRvc);
    }

    protected void postUpdateSessions() {
        if (this.mInit) {
            this.mHandler.sendEmptyMessage(1);
        }
    }

    public void destroy() {
        if (D.BUG) {
            Log.d(TAG, "destroy");
        }
        this.mInit = false;
        this.mMgr.removeOnActiveSessionsChangedListener(this.mSessionsListener);
        this.mMgr.unregisterRemoteVolumeController(this.mRvc);
    }

    public void setVolume(MediaSession.Token token, int level) {
        MediaControllerRecord r = this.mRecords.get(token);
        if (r == null) {
            String str = TAG;
            Log.w(str, "setVolume: No record found for token " + token);
            return;
        }
        if (D.BUG) {
            String str2 = TAG;
            Log.d(str2, "Setting level to " + level);
        }
        r.controller.setVolumeTo(level, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onRemoteVolumeChangedH(MediaSession.Token sessionToken, int flags) {
        MediaController controller = new MediaController(this.mContext, sessionToken);
        if (D.BUG) {
            String str = TAG;
            Log.d(str, "remoteVolumeChangedH " + controller.getPackageName() + " " + Util.audioManagerFlagsToString(flags));
        }
        MediaSession.Token token = controller.getSessionToken();
        this.mCallbacks.onRemoteVolumeChanged(token, flags);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onUpdateRemoteControllerH(MediaSession.Token sessionToken) {
        MediaController controller = sessionToken != null ? new MediaController(this.mContext, sessionToken) : null;
        String pkg = controller != null ? controller.getPackageName() : null;
        if (D.BUG) {
            String str = TAG;
            Log.d(str, "updateRemoteControllerH " + pkg);
        }
        postUpdateSessions();
    }

    protected void onActiveSessionsUpdatedH(List<MediaController> controllers) {
        if (D.BUG) {
            String str = TAG;
            Log.d(str, "onActiveSessionsUpdatedH n=" + controllers.size());
        }
        Set<MediaSession.Token> toRemove = new HashSet<>(this.mRecords.keySet());
        for (MediaController controller : controllers) {
            MediaSession.Token token = controller.getSessionToken();
            MediaController.PlaybackInfo pi = controller.getPlaybackInfo();
            toRemove.remove(token);
            if (!this.mRecords.containsKey(token)) {
                MediaControllerRecord r = new MediaControllerRecord(controller);
                r.name = getControllerName(controller);
                this.mRecords.put(token, r);
                controller.registerCallback(r, this.mHandler);
            }
            MediaControllerRecord r2 = this.mRecords.get(token);
            boolean remote = isRemote(pi);
            if (remote) {
                updateRemoteH(token, r2.name, pi);
                r2.sentRemote = true;
            }
        }
        for (MediaSession.Token t : toRemove) {
            MediaControllerRecord r3 = this.mRecords.get(t);
            r3.controller.unregisterCallback(r3);
            this.mRecords.remove(t);
            if (D.BUG) {
                String str2 = TAG;
                Log.d(str2, "Removing " + r3.name + " sentRemote=" + r3.sentRemote);
            }
            if (r3.sentRemote) {
                this.mCallbacks.onRemoteRemoved(t);
                r3.sentRemote = false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isRemote(MediaController.PlaybackInfo pi) {
        return pi != null && pi.getPlaybackType() == 2;
    }

    protected String getControllerName(MediaController controller) {
        String appLabel;
        PackageManager pm = this.mContext.getPackageManager();
        String pkg = controller.getPackageName();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            appLabel = Objects.toString(ai.loadLabel(pm), "").trim();
        } catch (PackageManager.NameNotFoundException e) {
        }
        if (appLabel.length() > 0) {
            return appLabel;
        }
        return pkg;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateRemoteH(MediaSession.Token token, String name, MediaController.PlaybackInfo pi) {
        Callbacks callbacks = this.mCallbacks;
        if (callbacks != null) {
            callbacks.onRemoteUpdate(token, name, pi);
        }
    }

    private static void dump(int n, PrintWriter writer, MediaController c) {
        writer.println("  Controller " + n + ": " + c.getPackageName());
        Bundle extras = c.getExtras();
        long flags = c.getFlags();
        MediaMetadata mm = c.getMetadata();
        MediaController.PlaybackInfo pi = c.getPlaybackInfo();
        PlaybackState playbackState = c.getPlaybackState();
        List<MediaSession.QueueItem> queue = c.getQueue();
        CharSequence queueTitle = c.getQueueTitle();
        int ratingType = c.getRatingType();
        PendingIntent sessionActivity = c.getSessionActivity();
        writer.println("    PlaybackState: " + Util.playbackStateToString(playbackState));
        writer.println("    PlaybackInfo: " + Util.playbackInfoToString(pi));
        if (mm != null) {
            writer.println("  MediaMetadata.desc=" + mm.getDescription());
        }
        writer.println("    RatingType: " + ratingType);
        writer.println("    Flags: " + flags);
        if (extras != null) {
            writer.println("    Extras:");
            for (String key : extras.keySet()) {
                writer.println("      " + key + "=" + extras.get(key));
            }
        }
        if (queueTitle != null) {
            writer.println("    QueueTitle: " + ((Object) queueTitle));
        }
        if (queue != null && !queue.isEmpty()) {
            writer.println("    Queue:");
            for (MediaSession.QueueItem qi : queue) {
                writer.println("      " + qi);
            }
        }
        if (pi != null) {
            writer.println("    sessionActivity: " + sessionActivity);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public final class MediaControllerRecord extends MediaController.Callback {
        public final MediaController controller;
        public String name;
        public boolean sentRemote;

        private MediaControllerRecord(MediaController controller) {
            this.controller = controller;
        }

        private String cb(String method) {
            return method + " " + this.controller.getPackageName() + " ";
        }

        @Override // android.media.session.MediaController.Callback
        public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onAudioInfoChanged") + Util.playbackInfoToString(info) + " sentRemote=" + this.sentRemote);
            }
            boolean remote = MediaSessions.isRemote(info);
            if (!remote && this.sentRemote) {
                MediaSessions.this.mCallbacks.onRemoteRemoved(this.controller.getSessionToken());
                this.sentRemote = false;
            } else if (remote) {
                MediaSessions.this.updateRemoteH(this.controller.getSessionToken(), this.name, info);
                this.sentRemote = true;
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onExtrasChanged(Bundle extras) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onExtrasChanged") + extras);
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onMetadataChanged(MediaMetadata metadata) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onMetadataChanged") + Util.mediaMetadataToString(metadata));
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onPlaybackStateChanged(PlaybackState state) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onPlaybackStateChanged") + Util.playbackStateToString(state));
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onQueueChanged") + queue);
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onQueueTitleChanged(CharSequence title) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onQueueTitleChanged") + ((Object) title));
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onSessionDestroyed() {
            if (D.BUG) {
                Log.d(MediaSessions.TAG, cb("onSessionDestroyed"));
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onSessionEvent(String event, Bundle extras) {
            if (D.BUG) {
                String str = MediaSessions.TAG;
                Log.d(str, cb("onSessionEvent") + "event=" + event + " extras=" + extras);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public final class H extends Handler {
        private static final int REMOTE_VOLUME_CHANGED = 2;
        private static final int UPDATE_REMOTE_CONTROLLER = 3;
        private static final int UPDATE_SESSIONS = 1;

        private H(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 1) {
                MediaSessions mediaSessions = MediaSessions.this;
                mediaSessions.onActiveSessionsUpdatedH(mediaSessions.mMgr.getActiveSessions(null));
            } else if (i == 2) {
                MediaSessions.this.onRemoteVolumeChangedH((MediaSession.Token) msg.obj, msg.arg1);
            } else if (i == 3) {
                MediaSessions.this.onUpdateRemoteControllerH((MediaSession.Token) msg.obj);
            }
        }
    }
}
