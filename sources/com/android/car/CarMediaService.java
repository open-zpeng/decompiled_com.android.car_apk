package com.android.car;

import android.app.ActivityManager;
import android.car.media.ICarMedia;
import android.car.media.ICarMediaSourceListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/* loaded from: classes3.dex */
public class CarMediaService extends ICarMedia.Stub implements CarServiceBase {
    private static final int AUTOPLAY_CONFIG_ALWAYS = 1;
    private static final int AUTOPLAY_CONFIG_NEVER = 0;
    private static final int AUTOPLAY_CONFIG_RETAIN_PER_SOURCE = 2;
    private static final int AUTOPLAY_CONFIG_RETAIN_PREVIOUS = 3;
    private static final String COMPONENT_NAME_SEPARATOR = ",";
    private static final String EXTRA_AUTOPLAY = "com.android.car.media.autoplay";
    private static final String MEDIA_CONNECTION_ACTION = "com.android.car.media.MEDIA_CONNECTION";
    private static final String PLAYBACK_STATE_KEY = "playback_state";
    private static final String SHARED_PREF = "com.android.car.media.car_media_service";
    private static final String SOURCE_KEY = "media_source_component";
    private MediaController mActiveUserMediaController;
    private final Context mContext;
    private int mCurrentPlaybackState;
    private int mCurrentUser;
    private final Handler mHandler;
    private boolean mIsPackageUpdateReceiverRegistered;
    private final MediaSessionManager mMediaSessionManager;
    private final IntentFilter mPackageUpdateFilter;
    private boolean mPendingInit;
    private int mPlayOnBootConfig;
    private int mPlayOnMediaSourceChangedConfig;
    private ComponentName mPreviousMediaComponent;
    private ComponentName mPrimaryMediaComponent;
    private String mRemovedMediaSourcePackage;
    private SessionChangedListener mSessionsListener;
    private SharedPreferences mSharedPrefs;
    private final UserManager mUserManager;
    private final MediaSessionUpdater mMediaSessionUpdater = new MediaSessionUpdater();
    private final RemoteCallbackList<ICarMediaSourceListener> mMediaSourceListeners = new RemoteCallbackList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver mPackageUpdateReceiver = new BroadcastReceiver() { // from class: com.android.car.CarMediaService.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            ComponentName mediaSource;
            if (intent.getData() == null) {
                return;
            }
            String intentPackage = intent.getData().getSchemeSpecificPart();
            if ("android.intent.action.PACKAGE_REMOVED".equals(intent.getAction())) {
                if (CarMediaService.this.mPrimaryMediaComponent != null && CarMediaService.this.mPrimaryMediaComponent.getPackageName().equals(intentPackage)) {
                    CarMediaService.this.mRemovedMediaSourcePackage = intentPackage;
                    CarMediaService.this.setPrimaryMediaSource(null);
                }
            } else if (("android.intent.action.PACKAGE_REPLACED".equals(intent.getAction()) || "android.intent.action.PACKAGE_ADDED".equals(intent.getAction())) && CarMediaService.this.mRemovedMediaSourcePackage != null && CarMediaService.this.mRemovedMediaSourcePackage.equals(intentPackage) && (mediaSource = CarMediaService.this.getMediaSource(intentPackage, "")) != null) {
                CarMediaService.this.setPrimaryMediaSource(mediaSource);
            }
        }
    };
    private final BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() { // from class: com.android.car.CarMediaService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            CarMediaService.this.mCurrentUser = ActivityManager.getCurrentUser();
            if (Log.isLoggable(CarLog.TAG_MEDIA, 3)) {
                Slog.d(CarLog.TAG_MEDIA, "Switched to user " + CarMediaService.this.mCurrentUser);
            }
            CarMediaService.this.maybeInitUser();
        }
    };
    private MediaController.Callback mMediaControllerCallback = new MediaController.Callback() { // from class: com.android.car.CarMediaService.4
        @Override // android.media.session.MediaController.Callback
        public void onPlaybackStateChanged(PlaybackState state) {
            if (!CarMediaService.this.isCurrentUserEphemeral()) {
                CarMediaService.this.savePlaybackState(state);
            }
        }
    };
    private final HandlerThread mHandlerThread = new HandlerThread(CarLog.TAG_MEDIA);

    public CarMediaService(Context context) {
        this.mContext = context;
        this.mUserManager = (UserManager) this.mContext.getSystemService("user");
        this.mMediaSessionManager = (MediaSessionManager) this.mContext.getSystemService(MediaSessionManager.class);
        this.mHandlerThread.start();
        this.mHandler = new Handler(this.mHandlerThread.getLooper());
        this.mPackageUpdateFilter = new IntentFilter();
        this.mPackageUpdateFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        this.mPackageUpdateFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        this.mPackageUpdateFilter.addAction("android.intent.action.PACKAGE_ADDED");
        this.mPackageUpdateFilter.addDataScheme("package");
        IntentFilter userSwitchFilter = new IntentFilter();
        userSwitchFilter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiver(this.mUserSwitchReceiver, userSwitchFilter);
        this.mPlayOnMediaSourceChangedConfig = this.mContext.getResources().getInteger(R.integer.config_mediaSourceChangedAutoplay);
        this.mPlayOnBootConfig = this.mContext.getResources().getInteger(R.integer.config_mediaBootAutoplay);
        this.mCurrentUser = ActivityManager.getCurrentUser();
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        maybeInitUser();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void maybeInitUser() {
        int i = this.mCurrentUser;
        if (i == 0) {
            return;
        }
        if (this.mUserManager.isUserUnlocked(i)) {
            initUser();
        } else {
            this.mPendingInit = true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initUser() {
        if (this.mSharedPrefs == null) {
            this.mSharedPrefs = this.mContext.getSharedPreferences(SHARED_PREF, 0);
        }
        if (this.mIsPackageUpdateReceiverRegistered) {
            this.mContext.unregisterReceiver(this.mPackageUpdateReceiver);
        }
        UserHandle currentUser = new UserHandle(this.mCurrentUser);
        this.mContext.registerReceiverAsUser(this.mPackageUpdateReceiver, currentUser, this.mPackageUpdateFilter, null, null);
        this.mIsPackageUpdateReceiverRegistered = true;
        this.mPrimaryMediaComponent = isCurrentUserEphemeral() ? getDefaultMediaSource() : getLastMediaSource();
        this.mActiveUserMediaController = null;
        updateMediaSessionCallbackForCurrentUser();
        notifyListeners();
        startMediaConnectorService(shouldStartPlayback(this.mPlayOnBootConfig), currentUser);
    }

    private void startMediaConnectorService(boolean startPlayback, UserHandle currentUser) {
        Intent serviceStart = new Intent(MEDIA_CONNECTION_ACTION);
        serviceStart.setPackage(this.mContext.getResources().getString(R.string.serviceMediaConnection));
        serviceStart.putExtra(EXTRA_AUTOPLAY, startPlayback);
        this.mContext.startForegroundServiceAsUser(serviceStart, currentUser);
    }

    private boolean sharedPrefsInitialized() {
        StackTraceElement[] stackTrace;
        if (this.mSharedPrefs == null) {
            Slog.e(CarLog.TAG_MEDIA, "SharedPreferences are not initialized!");
            String className = getClass().getName();
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                String log = ste.toString();
                if (log.contains(className)) {
                    Slog.e(CarLog.TAG_MEDIA, log);
                }
            }
            return false;
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isCurrentUserEphemeral() {
        return this.mUserManager.getUserInfo(this.mCurrentUser).isEphemeral();
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        this.mMediaSessionUpdater.unregisterCallbacks();
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*CarMediaService*");
        StringBuilder sb = new StringBuilder();
        sb.append("\tCurrent media component: ");
        ComponentName componentName = this.mPrimaryMediaComponent;
        sb.append(componentName == null ? "-" : componentName.flattenToString());
        writer.println(sb.toString());
        StringBuilder sb2 = new StringBuilder();
        sb2.append("\tPrevious media component: ");
        ComponentName componentName2 = this.mPreviousMediaComponent;
        sb2.append(componentName2 != null ? componentName2.flattenToString() : "-");
        writer.println(sb2.toString());
        if (this.mActiveUserMediaController != null) {
            writer.println("\tCurrent media controller: " + this.mActiveUserMediaController.getPackageName());
            writer.println("\tCurrent browse service extra: " + getClassName(this.mActiveUserMediaController));
        }
        writer.println("\tNumber of active media sessions: " + this.mMediaSessionManager.getActiveSessionsForUser(null, ActivityManager.getCurrentUser()).size());
    }

    public synchronized void setMediaSource(@NonNull ComponentName componentName) {
        ICarImpl.assertPermission(this.mContext, "android.permission.MEDIA_CONTENT_CONTROL");
        if (Log.isLoggable(CarLog.TAG_MEDIA, 3)) {
            Slog.d(CarLog.TAG_MEDIA, "Changing media source to: " + componentName.getPackageName());
        }
        setPrimaryMediaSource(componentName);
    }

    public synchronized ComponentName getMediaSource() {
        ICarImpl.assertPermission(this.mContext, "android.permission.MEDIA_CONTENT_CONTROL");
        return this.mPrimaryMediaComponent;
    }

    public synchronized void registerMediaSourceListener(ICarMediaSourceListener callback) {
        ICarImpl.assertPermission(this.mContext, "android.permission.MEDIA_CONTENT_CONTROL");
        this.mMediaSourceListeners.register(callback);
    }

    public synchronized void unregisterMediaSourceListener(ICarMediaSourceListener callback) {
        ICarImpl.assertPermission(this.mContext, "android.permission.MEDIA_CONTENT_CONTROL");
        this.mMediaSourceListeners.unregister(callback);
    }

    public void setUserLockStatus(final int userHandle, final boolean unlocked) {
        this.mMainHandler.post(new Runnable() { // from class: com.android.car.CarMediaService.3
            @Override // java.lang.Runnable
            public void run() {
                int i;
                if (Log.isLoggable(CarLog.TAG_MEDIA, 3)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("User ");
                    sb.append(userHandle);
                    sb.append(" is ");
                    sb.append(unlocked ? "unlocked" : "locked");
                    Slog.d(CarLog.TAG_MEDIA, sb.toString());
                }
                if (unlocked && (i = userHandle) != 0 && i == ActivityManager.getCurrentUser() && CarMediaService.this.mPendingInit) {
                    CarMediaService.this.initUser();
                    CarMediaService.this.mPendingInit = false;
                }
            }
        });
    }

    private void updateMediaSessionCallbackForCurrentUser() {
        SessionChangedListener sessionChangedListener = this.mSessionsListener;
        if (sessionChangedListener != null) {
            this.mMediaSessionManager.removeOnActiveSessionsChangedListener(sessionChangedListener);
        }
        this.mSessionsListener = new SessionChangedListener(ActivityManager.getCurrentUser());
        this.mMediaSessionManager.addOnActiveSessionsChangedListener(this.mSessionsListener, null, ActivityManager.getCurrentUser(), null);
        this.mMediaSessionUpdater.registerCallbacks(this.mMediaSessionManager.getActiveSessionsForUser(null, ActivityManager.getCurrentUser()));
    }

    private void play() {
        if (this.mActiveUserMediaController != null) {
            if (Log.isLoggable(CarLog.TAG_MEDIA, 3)) {
                Slog.d(CarLog.TAG_MEDIA, "playing " + this.mActiveUserMediaController.getPackageName());
            }
            MediaController.TransportControls controls = this.mActiveUserMediaController.getTransportControls();
            if (controls != null) {
                controls.play();
                return;
            }
            Slog.e(CarLog.TAG_MEDIA, "Can't start playback, transport controls unavailable " + this.mActiveUserMediaController.getPackageName());
        }
    }

    private void stopAndUnregisterCallback() {
        MediaController mediaController = this.mActiveUserMediaController;
        if (mediaController != null) {
            mediaController.unregisterCallback(this.mMediaControllerCallback);
            if (Log.isLoggable(CarLog.TAG_MEDIA, 3)) {
                Slog.d(CarLog.TAG_MEDIA, "stopping " + this.mActiveUserMediaController.getPackageName());
            }
            MediaController.TransportControls controls = this.mActiveUserMediaController.getTransportControls();
            if (controls != null) {
                controls.stop();
                return;
            }
            Slog.e(CarLog.TAG_MEDIA, "Can't stop playback, transport controls unavailable " + this.mActiveUserMediaController.getPackageName());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class SessionChangedListener implements MediaSessionManager.OnActiveSessionsChangedListener {
        private final int mCurrentUser;

        SessionChangedListener(int currentUser) {
            this.mCurrentUser = currentUser;
        }

        @Override // android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            if (ActivityManager.getCurrentUser() != this.mCurrentUser) {
                Slog.e(CarLog.TAG_MEDIA, "Active session callback for old user: " + this.mCurrentUser);
                return;
            }
            CarMediaService.this.mMediaSessionUpdater.registerCallbacks(controllers);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class MediaControllerCallback extends MediaController.Callback {
        private final MediaController mMediaController;
        private int mPreviousPlaybackState;

        private MediaControllerCallback(MediaController mediaController) {
            this.mMediaController = mediaController;
            PlaybackState state = mediaController.getPlaybackState();
            this.mPreviousPlaybackState = state == null ? 0 : state.getState();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void register() {
            this.mMediaController.registerCallback(this);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void unregister() {
            this.mMediaController.unregisterCallback(this);
        }

        @Override // android.media.session.MediaController.Callback
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (state.getState() == 3 && state.getState() != this.mPreviousPlaybackState) {
                ComponentName mediaSource = CarMediaService.this.getMediaSource(this.mMediaController.getPackageName(), CarMediaService.getClassName(this.mMediaController));
                if (mediaSource != null && !mediaSource.equals(CarMediaService.this.mPrimaryMediaComponent) && Log.isLoggable(CarLog.TAG_MEDIA, 4)) {
                    Slog.i(CarLog.TAG_MEDIA, "Changing media source due to playback state change: " + mediaSource.flattenToString());
                }
                CarMediaService.this.setPrimaryMediaSource(mediaSource);
            }
            this.mPreviousPlaybackState = state.getState();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class MediaSessionUpdater {
        private Map<MediaSession.Token, MediaControllerCallback> mCallbacks;

        private MediaSessionUpdater() {
            this.mCallbacks = new HashMap();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void registerCallbacks(List<MediaController> newControllers) {
            List<MediaController> additions = new ArrayList<>(newControllers.size());
            Map<MediaSession.Token, MediaControllerCallback> updatedCallbacks = new HashMap<>(newControllers.size());
            for (MediaController controller : newControllers) {
                MediaSession.Token token = controller.getSessionToken();
                MediaControllerCallback callback = this.mCallbacks.get(token);
                if (callback == null) {
                    callback = new MediaControllerCallback(controller);
                    callback.register();
                    additions.add(controller);
                }
                updatedCallbacks.put(token, callback);
            }
            for (MediaSession.Token token2 : this.mCallbacks.keySet()) {
                if (!updatedCallbacks.containsKey(token2)) {
                    this.mCallbacks.get(token2).unregister();
                }
            }
            this.mCallbacks = updatedCallbacks;
            CarMediaService.this.updatePrimaryMediaSourceWithCurrentlyPlaying(additions);
            if (CarMediaService.this.mActiveUserMediaController == null) {
                CarMediaService.this.updateActiveMediaController(newControllers);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void unregisterCallbacks() {
            for (Map.Entry<MediaSession.Token, MediaControllerCallback> entry : this.mCallbacks.entrySet()) {
                entry.getValue().unregister();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void setPrimaryMediaSource(@Nullable ComponentName componentName) {
        if (this.mPrimaryMediaComponent == null || !this.mPrimaryMediaComponent.equals(componentName)) {
            stopAndUnregisterCallback();
            this.mActiveUserMediaController = null;
            this.mPreviousMediaComponent = this.mPrimaryMediaComponent;
            this.mPrimaryMediaComponent = componentName;
            if (this.mPrimaryMediaComponent != null && !TextUtils.isEmpty(this.mPrimaryMediaComponent.flattenToString())) {
                if (!isCurrentUserEphemeral()) {
                    saveLastMediaSource(this.mPrimaryMediaComponent);
                }
                this.mRemovedMediaSourcePackage = null;
            }
            notifyListeners();
            startMediaConnectorService(shouldStartPlayback(this.mPlayOnMediaSourceChangedConfig), new UserHandle(this.mCurrentUser));
            this.mCurrentPlaybackState = 0;
            updateActiveMediaController(this.mMediaSessionManager.getActiveSessionsForUser(null, ActivityManager.getCurrentUser()));
        }
    }

    private void notifyListeners() {
        int i = this.mMediaSourceListeners.beginBroadcast();
        while (true) {
            int i2 = i - 1;
            if (i > 0) {
                try {
                    ICarMediaSourceListener callback = this.mMediaSourceListeners.getBroadcastItem(i2);
                    callback.onMediaSourceChanged(this.mPrimaryMediaComponent);
                } catch (RemoteException e) {
                    Slog.e(CarLog.TAG_MEDIA, "calling onMediaSourceChanged failed " + e);
                }
                i = i2;
            } else {
                this.mMediaSourceListeners.finishBroadcast();
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void updatePrimaryMediaSourceWithCurrentlyPlaying(List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            if (controller.getPlaybackState() != null && controller.getPlaybackState().getState() == 3) {
                String newPackageName = controller.getPackageName();
                String newClassName = getClassName(controller);
                if (!matchPrimaryMediaSource(newPackageName, newClassName)) {
                    ComponentName mediaSource = getMediaSource(newPackageName, newClassName);
                    if (Log.isLoggable(CarLog.TAG_MEDIA, 4)) {
                        if (mediaSource != null) {
                            Slog.i(CarLog.TAG_MEDIA, "MediaController changed, updating media source to: " + mediaSource.flattenToString());
                        } else {
                            Slog.i(CarLog.TAG_MEDIA, "MediaController changed, but no media browse service found in package: " + newPackageName);
                        }
                    }
                    setPrimaryMediaSource(mediaSource);
                }
                return;
            }
        }
    }

    private boolean matchPrimaryMediaSource(@NonNull String newPackageName, @NonNull String newClassName) {
        ComponentName componentName = this.mPrimaryMediaComponent;
        if (componentName != null && componentName.getPackageName().equals(newPackageName)) {
            if (TextUtils.isEmpty(newClassName)) {
                return true;
            }
            return newClassName.equals(this.mPrimaryMediaComponent.getClassName());
        }
        return false;
    }

    private boolean isMediaService(@NonNull ComponentName componentName) {
        return getMediaService(componentName) != null;
    }

    private ComponentName getMediaService(@NonNull ComponentName componentName) {
        String packageName = componentName.getPackageName();
        String className = componentName.getClassName();
        PackageManager packageManager = this.mContext.getPackageManager();
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserServiceCompat.SERVICE_INTERFACE);
        List<ResolveInfo> mediaServices = packageManager.queryIntentServicesAsUser(mediaIntent, 64, ActivityManager.getCurrentUser());
        for (ResolveInfo service : mediaServices) {
            String serviceName = service.serviceInfo.name;
            if (!TextUtils.isEmpty(serviceName) && (TextUtils.isEmpty(className) || serviceName.equals(className))) {
                return new ComponentName(packageName, serviceName);
            }
        }
        if (Log.isLoggable(CarLog.TAG_MEDIA, 3)) {
            Slog.d(CarLog.TAG_MEDIA, "No MediaBrowseService with ComponentName: " + componentName.flattenToString());
            return null;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    @Nullable
    public ComponentName getMediaSource(@NonNull String packageName, @NonNull String className) {
        return getMediaService(new ComponentName(packageName, className));
    }

    private void saveLastMediaSource(@NonNull ComponentName component) {
        if (!sharedPrefsInitialized()) {
            return;
        }
        String componentName = component.flattenToString();
        String key = SOURCE_KEY + this.mCurrentUser;
        String serialized = this.mSharedPrefs.getString(key, null);
        if (serialized == null) {
            this.mSharedPrefs.edit().putString(key, componentName).apply();
            return;
        }
        Deque<String> componentNames = getComponentNameList(serialized);
        componentNames.remove(componentName);
        componentNames.addFirst(componentName);
        this.mSharedPrefs.edit().putString(key, serializeComponentNameList(componentNames)).apply();
    }

    private ComponentName getLastMediaSource() {
        if (sharedPrefsInitialized()) {
            String key = SOURCE_KEY + this.mCurrentUser;
            String serialized = this.mSharedPrefs.getString(key, null);
            if (!TextUtils.isEmpty(serialized)) {
                for (String name : getComponentNameList(serialized)) {
                    ComponentName componentName = ComponentName.unflattenFromString(name);
                    if (isMediaService(componentName)) {
                        return componentName;
                    }
                }
            }
        }
        return getDefaultMediaSource();
    }

    private ComponentName getDefaultMediaSource() {
        String defaultMediaSource = this.mContext.getString(R.string.default_media_source);
        ComponentName defaultComponent = ComponentName.unflattenFromString(defaultMediaSource);
        if (isMediaService(defaultComponent)) {
            return defaultComponent;
        }
        return null;
    }

    private String serializeComponentNameList(Deque<String> componentNames) {
        return (String) componentNames.stream().collect(Collectors.joining(COMPONENT_NAME_SEPARATOR));
    }

    private Deque<String> getComponentNameList(String serialized) {
        String[] componentNames = serialized.split(COMPONENT_NAME_SEPARATOR);
        return new ArrayDeque(Arrays.asList(componentNames));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void savePlaybackState(PlaybackState playbackState) {
        if (!sharedPrefsInitialized()) {
            return;
        }
        int state = playbackState != null ? playbackState.getState() : 0;
        this.mCurrentPlaybackState = state;
        String key = getPlaybackStateKey();
        this.mSharedPrefs.edit().putInt(key, state).apply();
    }

    private String getPlaybackStateKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(PLAYBACK_STATE_KEY);
        sb.append(this.mCurrentUser);
        ComponentName componentName = this.mPrimaryMediaComponent;
        sb.append(componentName == null ? "" : componentName.flattenToString());
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateActiveMediaController(List<MediaController> mediaControllers) {
        if (this.mPrimaryMediaComponent == null) {
            return;
        }
        MediaController mediaController = this.mActiveUserMediaController;
        if (mediaController != null) {
            mediaController.unregisterCallback(this.mMediaControllerCallback);
            this.mActiveUserMediaController = null;
        }
        for (MediaController controller : mediaControllers) {
            if (matchPrimaryMediaSource(controller.getPackageName(), getClassName(controller))) {
                this.mActiveUserMediaController = controller;
                PlaybackState state = this.mActiveUserMediaController.getPlaybackState();
                if (!isCurrentUserEphemeral()) {
                    savePlaybackState(state);
                }
                this.mActiveUserMediaController.registerCallback(this.mMediaControllerCallback, this.mHandler);
                return;
            }
        }
    }

    private boolean shouldStartPlayback(int config) {
        if (config != 0) {
            if (config != 1) {
                if (config == 2) {
                    return sharedPrefsInitialized() && this.mSharedPrefs.getInt(getPlaybackStateKey(), 0) == 3;
                } else if (config == 3) {
                    return this.mCurrentPlaybackState == 3;
                } else {
                    Slog.e(CarLog.TAG_MEDIA, "Unsupported playback configuration: " + config);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    @NonNull
    public static String getClassName(@NonNull MediaController controller) {
        Bundle sessionExtras = controller.getExtras();
        String value = sessionExtras == null ? "" : sessionExtras.getString("android.media.session.BROWSE_SERVICE");
        return value != null ? value : "";
    }
}
