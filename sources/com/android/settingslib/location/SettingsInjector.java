package com.android.settingslib.location;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import androidx.preference.Preference;
import com.android.settingslib.R;
import com.android.settingslib.location.InjectedSetting;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
public class SettingsInjector {
    private static final long INJECTED_STATUS_UPDATE_TIMEOUT_MILLIS = 1000;
    static final String TAG = "SettingsInjector";
    private static final int WHAT_RECEIVED_STATUS = 2;
    private static final int WHAT_RELOAD = 1;
    private static final int WHAT_TIMEOUT = 3;
    private final Context mContext;
    protected final Set<Setting> mSettings = new HashSet();
    private final Handler mHandler = new StatusLoadingHandler(this.mSettings);

    public SettingsInjector(Context context) {
        this.mContext = context;
    }

    protected List<InjectedSetting> getSettings(UserHandle userHandle) {
        PackageManager pm = this.mContext.getPackageManager();
        Intent intent = new Intent("android.location.SettingInjectorService");
        int profileId = userHandle.getIdentifier();
        List<ResolveInfo> resolveInfos = pm.queryIntentServicesAsUser(intent, 128, profileId);
        if (Log.isLoggable(TAG, 3)) {
            Log.d(TAG, "Found services for profile id " + profileId + ": " + resolveInfos);
        }
        List<InjectedSetting> settings = new ArrayList<>(resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                InjectedSetting setting = parseServiceInfo(resolveInfo, userHandle, pm);
                if (setting == null) {
                    Log.w(TAG, "Unable to load service info " + resolveInfo);
                } else {
                    settings.add(setting);
                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to load service info " + resolveInfo, e);
            } catch (XmlPullParserException e2) {
                Log.w(TAG, "Unable to load service info " + resolveInfo, e2);
            }
        }
        if (Log.isLoggable(TAG, 3)) {
            Log.d(TAG, "Loaded settings for profile id " + profileId + ": " + settings);
        }
        return settings;
    }

    private void populatePreference(Preference preference, InjectedSetting setting) {
        preference.setTitle(setting.title);
        preference.setSummary(R.string.loading_injected_setting_summary);
        preference.setOnPreferenceClickListener(new ServiceSettingClickedListener(setting));
    }

    public Map<Integer, List<Preference>> getInjectedSettings(Context prefContext, int profileId) {
        UserManager um = (UserManager) this.mContext.getSystemService("user");
        List<UserHandle> profiles = um.getUserProfiles();
        ArrayMap<Integer, List<Preference>> result = new ArrayMap<>();
        this.mSettings.clear();
        for (UserHandle userHandle : profiles) {
            if (profileId == -2 || profileId == userHandle.getIdentifier()) {
                List<Preference> prefs = new ArrayList<>();
                Iterable<InjectedSetting> settings = getSettings(userHandle);
                for (InjectedSetting setting : settings) {
                    Preference preference = createPreference(prefContext, setting);
                    populatePreference(preference, setting);
                    prefs.add(preference);
                    this.mSettings.add(new Setting(setting, preference));
                }
                if (!prefs.isEmpty()) {
                    result.put(Integer.valueOf(userHandle.getIdentifier()), prefs);
                }
            }
        }
        reloadStatusMessages();
        return result;
    }

    protected Preference createPreference(Context prefContext, InjectedSetting setting) {
        return new Preference(prefContext);
    }

    private static InjectedSetting parseServiceInfo(ResolveInfo service, UserHandle userHandle, PackageManager pm) throws XmlPullParserException, IOException {
        ServiceInfo si = service.serviceInfo;
        ApplicationInfo ai = si.applicationInfo;
        if ((ai.flags & 1) == 0 && Log.isLoggable(TAG, 5)) {
            Log.w(TAG, "Ignoring attempt to inject setting from app not in system image: " + service);
            return null;
        }
        XmlResourceParser parser = null;
        try {
            try {
                XmlResourceParser parser2 = si.loadXmlMetaData(pm, "android.location.SettingInjectorService");
                if (parser2 == null) {
                    throw new XmlPullParserException("No android.location.SettingInjectorService meta-data for " + service + ": " + si);
                }
                AttributeSet attrs = Xml.asAttributeSet(parser2);
                while (true) {
                    int type = parser2.next();
                    if (type == 1 || type == 2) {
                        break;
                    }
                }
                String nodeName = parser2.getName();
                if (!"injected-location-setting".equals(nodeName)) {
                    throw new XmlPullParserException("Meta-data does not start with injected-location-setting tag");
                }
                Resources res = pm.getResourcesForApplicationAsUser(si.packageName, userHandle.getIdentifier());
                InjectedSetting parseAttributes = parseAttributes(si.packageName, si.name, userHandle, res, attrs);
                parser2.close();
                return parseAttributes;
            } catch (PackageManager.NameNotFoundException e) {
                throw new XmlPullParserException("Unable to load resources for package " + si.packageName);
            }
        } catch (Throwable th) {
            if (0 != 0) {
                parser.close();
            }
            throw th;
        }
    }

    private static InjectedSetting parseAttributes(String packageName, String className, UserHandle userHandle, Resources res, AttributeSet attrs) {
        TypedArray sa = res.obtainAttributes(attrs, android.R.styleable.SettingInjectorService);
        try {
            String title = sa.getString(1);
            int iconId = sa.getResourceId(0, 0);
            String settingsActivity = sa.getString(2);
            String userRestriction = sa.getString(3);
            if (Log.isLoggable(TAG, 3)) {
                Log.d(TAG, "parsed title: " + title + ", iconId: " + iconId + ", settingsActivity: " + settingsActivity);
            }
            return new InjectedSetting.Builder().setPackageName(packageName).setClassName(className).setTitle(title).setIconId(iconId).setUserHandle(userHandle).setSettingsActivity(settingsActivity).setUserRestriction(userRestriction).build();
        } finally {
            sa.recycle();
        }
    }

    public void reloadStatusMessages() {
        if (Log.isLoggable(TAG, 3)) {
            Log.d(TAG, "reloadingStatusMessages: " + this.mSettings);
        }
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(1));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    /* loaded from: classes3.dex */
    public class ServiceSettingClickedListener implements Preference.OnPreferenceClickListener {
        private InjectedSetting mInfo;

        public ServiceSettingClickedListener(InjectedSetting info) {
            this.mInfo = info;
        }

        @Override // androidx.preference.Preference.OnPreferenceClickListener
        public boolean onPreferenceClick(Preference preference) {
            Intent settingIntent = new Intent();
            settingIntent.setClassName(this.mInfo.packageName, this.mInfo.settingsActivity);
            settingIntent.setFlags(268468224);
            SettingsInjector.this.mContext.startActivityAsUser(settingIntent, this.mInfo.mUserHandle);
            return true;
        }
    }

    /* loaded from: classes3.dex */
    private static final class StatusLoadingHandler extends Handler {
        WeakReference<Set<Setting>> mAllSettings;
        private Set<Setting> mSettingsBeingLoaded;
        private Deque<Setting> mSettingsToLoad;

        public StatusLoadingHandler(Set<Setting> allSettings) {
            super(Looper.getMainLooper());
            this.mSettingsToLoad = new ArrayDeque();
            this.mSettingsBeingLoaded = new ArraySet();
            this.mAllSettings = new WeakReference<>(allSettings);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (Log.isLoggable(SettingsInjector.TAG, 3)) {
                Log.d(SettingsInjector.TAG, "handleMessage start: " + msg + ", " + this);
            }
            int i = msg.what;
            if (i == 1) {
                Set<Setting> allSettings = this.mAllSettings.get();
                if (allSettings != null) {
                    this.mSettingsToLoad.clear();
                    this.mSettingsToLoad.addAll(allSettings);
                }
            } else if (i != 2) {
                if (i == 3) {
                    Setting timedOutSetting = (Setting) msg.obj;
                    this.mSettingsBeingLoaded.remove(timedOutSetting);
                    if (Log.isLoggable(SettingsInjector.TAG, 5)) {
                        Log.w(SettingsInjector.TAG, "Timed out after " + timedOutSetting.getElapsedTime() + " millis trying to get status for: " + timedOutSetting);
                    }
                } else {
                    Log.wtf(SettingsInjector.TAG, "Unexpected what: " + msg);
                }
            } else {
                Setting receivedSetting = (Setting) msg.obj;
                receivedSetting.maybeLogElapsedTime();
                this.mSettingsBeingLoaded.remove(receivedSetting);
                removeMessages(3, receivedSetting);
            }
            if (this.mSettingsBeingLoaded.size() > 0) {
                if (Log.isLoggable(SettingsInjector.TAG, 2)) {
                    Log.v(SettingsInjector.TAG, "too many services already live for " + msg + ", " + this);
                }
            } else if (this.mSettingsToLoad.isEmpty()) {
                if (Log.isLoggable(SettingsInjector.TAG, 2)) {
                    Log.v(SettingsInjector.TAG, "nothing left to do for " + msg + ", " + this);
                }
            } else {
                Setting setting = this.mSettingsToLoad.removeFirst();
                setting.startService();
                this.mSettingsBeingLoaded.add(setting);
                Message timeoutMsg = obtainMessage(3, setting);
                sendMessageDelayed(timeoutMsg, SettingsInjector.INJECTED_STATUS_UPDATE_TIMEOUT_MILLIS);
                if (Log.isLoggable(SettingsInjector.TAG, 3)) {
                    Log.d(SettingsInjector.TAG, "handleMessage end " + msg + ", " + this + ", started loading " + setting);
                }
            }
        }

        @Override // android.os.Handler
        public String toString() {
            return "StatusLoadingHandler{mSettingsToLoad=" + this.mSettingsToLoad + ", mSettingsBeingLoaded=" + this.mSettingsBeingLoaded + '}';
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class MessengerHandler extends Handler {
        private Handler mHandler;
        private WeakReference<Setting> mSettingRef;

        public MessengerHandler(Setting setting, Handler handler) {
            this.mSettingRef = new WeakReference<>(setting);
            this.mHandler = handler;
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            Setting setting = this.mSettingRef.get();
            if (setting == null) {
                return;
            }
            Preference preference = setting.preference;
            Bundle bundle = msg.getData();
            boolean enabled = bundle.getBoolean("enabled", true);
            String summary = bundle.getString("summary", null);
            if (Log.isLoggable(SettingsInjector.TAG, 3)) {
                Log.d(SettingsInjector.TAG, setting + ": received " + msg + ", bundle: " + bundle);
            }
            preference.setSummary(summary);
            preference.setEnabled(enabled);
            Handler handler = this.mHandler;
            handler.sendMessage(handler.obtainMessage(2, setting));
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    /* loaded from: classes3.dex */
    public final class Setting {
        public final Preference preference;
        public final InjectedSetting setting;
        public long startMillis;

        public Setting(InjectedSetting setting, Preference preference) {
            this.setting = setting;
            this.preference = preference;
        }

        public String toString() {
            return "Setting{setting=" + this.setting + ", preference=" + this.preference + '}';
        }

        public void startService() {
            ActivityManager am = (ActivityManager) SettingsInjector.this.mContext.getSystemService("activity");
            if (!am.isUserRunning(this.setting.mUserHandle.getIdentifier())) {
                if (Log.isLoggable(SettingsInjector.TAG, 2)) {
                    Log.v(SettingsInjector.TAG, "Cannot start service as user " + this.setting.mUserHandle.getIdentifier() + " is not running");
                    return;
                }
                return;
            }
            Handler handler = new MessengerHandler(this, SettingsInjector.this.mHandler);
            Messenger messenger = new Messenger(handler);
            Intent intent = this.setting.getServiceIntent();
            intent.putExtra("messenger", messenger);
            if (Log.isLoggable(SettingsInjector.TAG, 3)) {
                Log.d(SettingsInjector.TAG, this.setting + ": sending update intent: " + intent + ", handler: " + handler);
                this.startMillis = SystemClock.elapsedRealtime();
            } else {
                this.startMillis = 0L;
            }
            SettingsInjector.this.mContext.startServiceAsUser(intent, this.setting.mUserHandle);
        }

        public long getElapsedTime() {
            long end = SystemClock.elapsedRealtime();
            return end - this.startMillis;
        }

        public void maybeLogElapsedTime() {
            if (Log.isLoggable(SettingsInjector.TAG, 3) && this.startMillis != 0) {
                long elapsed = getElapsedTime();
                Log.d(SettingsInjector.TAG, this + " update took " + elapsed + " millis");
            }
        }
    }
}
