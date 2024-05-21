package com.android.car;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.ICarBluetoothUserService;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import com.android.car.BluetoothProfileInhibitManager;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
/* loaded from: classes3.dex */
public class BluetoothProfileInhibitManager {
    private static final long RESTORE_BACKOFF_MILLIS = 1000;
    private static final String SETTINGS_DELIMITER = ",";
    private final ICarBluetoothUserService mBluetoothUserProxies;
    private final Context mContext;
    private final int mUserId;
    private static final String TAG = "BluetoothProfileInhibitManager";
    private static final boolean DBG = Log.isLoggable(TAG, 3);
    private static final Binder RESTORED_PROFILE_INHIBIT_TOKEN = new Binder();
    private final Object mProfileInhibitsLock = new Object();
    @GuardedBy({"mProfileInhibitsLock"})
    private final SetMultimap<BluetoothConnection, InhibitRecord> mProfileInhibits = new SetMultimap<>();
    @GuardedBy({"mProfileInhibitsLock"})
    private final HashSet<InhibitRecord> mRestoredInhibits = new HashSet<>();
    @GuardedBy({"mProfileInhibitsLock"})
    private final HashSet<BluetoothConnection> mAlreadyDisabledProfiles = new HashSet<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /* loaded from: classes3.dex */
    public static class BluetoothConnection {
        private static final String FLATTENED_PATTERN = "^(([0-9A-F]{2}:){5}[0-9A-F]{2}|null)/([0-9]+|null)$";
        private final BluetoothDevice mBluetoothDevice;
        private final Integer mBluetoothProfile;

        public BluetoothConnection(Integer profile, BluetoothDevice device) {
            this.mBluetoothProfile = profile;
            this.mBluetoothDevice = device;
        }

        public BluetoothDevice getDevice() {
            return this.mBluetoothDevice;
        }

        public Integer getProfile() {
            return this.mBluetoothProfile;
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof BluetoothConnection) {
                BluetoothConnection otherParams = (BluetoothConnection) other;
                return Objects.equals(this.mBluetoothDevice, otherParams.mBluetoothDevice) && Objects.equals(this.mBluetoothProfile, otherParams.mBluetoothProfile);
            }
            return false;
        }

        public int hashCode() {
            return Objects.hash(this.mBluetoothDevice, this.mBluetoothProfile);
        }

        public String toString() {
            return encode();
        }

        public String encode() {
            return this.mBluetoothDevice + "/" + this.mBluetoothProfile;
        }

        public static BluetoothConnection decode(String flattenedParams) {
            BluetoothDevice device;
            Integer profile;
            if (!flattenedParams.matches(FLATTENED_PATTERN)) {
                throw new IllegalArgumentException("Bad format for flattened BluetoothConnection");
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                return new BluetoothConnection(null, null);
            }
            String[] parts = flattenedParams.split("/");
            if (!"null".equals(parts[0])) {
                device = adapter.getRemoteDevice(parts[0]);
            } else {
                device = null;
            }
            if (!"null".equals(parts[1])) {
                profile = Integer.valueOf(parts[1]);
            } else {
                profile = null;
            }
            return new BluetoothConnection(profile, device);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class InhibitRecord implements IBinder.DeathRecipient {
        private final BluetoothConnection mParams;
        private boolean mRemoved = false;
        private final IBinder mToken;

        InhibitRecord(BluetoothConnection params, IBinder token) {
            this.mParams = params;
            this.mToken = token;
        }

        public BluetoothConnection getParams() {
            return this.mParams;
        }

        public IBinder getToken() {
            return this.mToken;
        }

        public boolean removeSelf() {
            synchronized (BluetoothProfileInhibitManager.this.mProfileInhibitsLock) {
                if (this.mRemoved) {
                    return true;
                }
                if (BluetoothProfileInhibitManager.this.removeInhibitRecord(this)) {
                    this.mRemoved = true;
                    return true;
                }
                return false;
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            BluetoothProfileInhibitManager bluetoothProfileInhibitManager = BluetoothProfileInhibitManager.this;
            bluetoothProfileInhibitManager.logd("Releasing inhibit request on profile " + Utils.getProfileName(this.mParams.getProfile().intValue()) + " for device " + this.mParams.getDevice() + ": requesting process died");
            removeSelf();
        }
    }

    public BluetoothProfileInhibitManager(Context context, int userId, ICarBluetoothUserService bluetoothUserProxies) {
        this.mContext = context;
        this.mUserId = userId;
        this.mBluetoothUserProxies = bluetoothUserProxies;
    }

    private void load() {
        String[] split;
        String savedBluetoothConnection = Settings.Secure.getStringForUser(this.mContext.getContentResolver(), "android.car.BLUETOOTH_PROFILES_INHIBITED", this.mUserId);
        if (TextUtils.isEmpty(savedBluetoothConnection)) {
            return;
        }
        logd("Restoring profile inhibits: " + savedBluetoothConnection);
        for (String paramsStr : savedBluetoothConnection.split(SETTINGS_DELIMITER)) {
            try {
                BluetoothConnection params = BluetoothConnection.decode(paramsStr);
                InhibitRecord record = new InhibitRecord(params, RESTORED_PROFILE_INHIBIT_TOKEN);
                this.mProfileInhibits.put(params, record);
                this.mRestoredInhibits.add(record);
                logd("Restored profile inhibits for " + params);
            } catch (IllegalArgumentException e) {
                loge("Bad format for saved profile inhibit: " + paramsStr + ", " + e);
            }
        }
    }

    private void commit() {
        Set<BluetoothConnection> inhibitedProfiles = new HashSet<>(this.mProfileInhibits.keySet());
        inhibitedProfiles.removeAll(this.mAlreadyDisabledProfiles);
        String savedDisconnects = (String) inhibitedProfiles.stream().map(new Function() { // from class: com.android.car.-$$Lambda$Q29oLPm5KBswrQNOk8EGYlo7bz4
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return ((BluetoothProfileInhibitManager.BluetoothConnection) obj).encode();
            }
        }).collect(Collectors.joining(SETTINGS_DELIMITER));
        Settings.Secure.putStringForUser(this.mContext.getContentResolver(), "android.car.BLUETOOTH_PROFILES_INHIBITED", savedDisconnects, this.mUserId);
        logd("Committed key: android.car.BLUETOOTH_PROFILES_INHIBITED, value: '" + savedDisconnects + "'");
    }

    public void start() {
        load();
        removeRestoredProfileInhibits();
    }

    public void stop() {
        releaseAllInhibitsBeforeUnbind();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean requestProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        logd("Request profile inhibit: profile " + Utils.getProfileName(profile) + ", device " + device.getAddress());
        BluetoothConnection params = new BluetoothConnection(Integer.valueOf(profile), device);
        InhibitRecord record = new InhibitRecord(params, token);
        return addInhibitRecord(record);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean releaseProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        logd("Release profile inhibit: profile " + Utils.getProfileName(profile) + ", device " + device.getAddress());
        BluetoothConnection params = new BluetoothConnection(Integer.valueOf(profile), device);
        InhibitRecord record = findInhibitRecord(params, token);
        if (record == null) {
            Slog.e(TAG, "Record not found");
            return false;
        }
        return record.removeSelf();
    }

    private boolean addInhibitRecord(InhibitRecord record) {
        synchronized (this.mProfileInhibitsLock) {
            BluetoothConnection params = record.getParams();
            if (isProxyAvailable(params.getProfile().intValue())) {
                Set<InhibitRecord> previousRecords = this.mProfileInhibits.get(params);
                if (findInhibitRecord(params, record.getToken()) != null) {
                    Slog.e(TAG, "Inhibit request already registered - skipping duplicate");
                    return false;
                }
                try {
                    record.getToken().linkToDeath(record, 0);
                    boolean isNewlyAdded = previousRecords.isEmpty();
                    this.mProfileInhibits.put(params, record);
                    if (isNewlyAdded) {
                        try {
                            int priority = this.mBluetoothUserProxies.getProfilePriority(params.getProfile().intValue(), params.getDevice());
                            if (priority == 0) {
                                this.mAlreadyDisabledProfiles.add(params);
                                logd("Profile " + Utils.getProfileName(params.getProfile().intValue()) + " already disabled for device " + params.getDevice() + " - suppressing re-enable");
                            } else {
                                this.mBluetoothUserProxies.setProfilePriority(params.getProfile().intValue(), params.getDevice(), 0);
                                this.mBluetoothUserProxies.bluetoothDisconnectFromProfile(params.getProfile().intValue(), params.getDevice());
                                logd("Disabled profile " + Utils.getProfileName(params.getProfile().intValue()) + " for device " + params.getDevice());
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Could not disable profile", e);
                            record.getToken().unlinkToDeath(record, 0);
                            this.mProfileInhibits.remove(params, record);
                            return false;
                        }
                    }
                    commit();
                    return true;
                } catch (RemoteException e2) {
                    Slog.e(TAG, "Could not link to death on inhibit token (already dead?)", e2);
                    return false;
                }
            }
            return false;
        }
    }

    private InhibitRecord findInhibitRecord(BluetoothConnection params, final IBinder token) {
        InhibitRecord orElse;
        synchronized (this.mProfileInhibitsLock) {
            orElse = this.mProfileInhibits.get(params).stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$BluetoothProfileInhibitManager$rBHUcTWwdCYXr5CoJJSNYyW4ESY
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return BluetoothProfileInhibitManager.lambda$findInhibitRecord$0(token, (BluetoothProfileInhibitManager.InhibitRecord) obj);
                }
            }).findAny().orElse(null);
        }
        return orElse;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$findInhibitRecord$0(IBinder token, InhibitRecord r) {
        return r.getToken() == token;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean removeInhibitRecord(InhibitRecord record) {
        synchronized (this.mProfileInhibitsLock) {
            BluetoothConnection params = record.getParams();
            if (isProxyAvailable(params.getProfile().intValue())) {
                if (!this.mProfileInhibits.containsEntry(params, record)) {
                    Slog.e(TAG, "Record already removed");
                    return true;
                } else if (this.mProfileInhibits.get(params).size() != 1 || restoreProfilePriority(params)) {
                    record.getToken().unlinkToDeath(record, 0);
                    this.mProfileInhibits.remove(params, record);
                    commit();
                    return true;
                } else {
                    return false;
                }
            }
            return false;
        }
    }

    private boolean restoreProfilePriority(BluetoothConnection params) {
        if (isProxyAvailable(params.getProfile().intValue())) {
            if (this.mAlreadyDisabledProfiles.remove(params)) {
                logd("Not restoring profile " + Utils.getProfileName(params.getProfile().intValue()) + " for device " + params.getDevice() + " - was manually disabled");
                return true;
            }
            try {
                this.mBluetoothUserProxies.setProfilePriority(params.getProfile().intValue(), params.getDevice(), 100);
                this.mBluetoothUserProxies.bluetoothConnectToProfile(params.getProfile().intValue(), params.getDevice());
                logd("Restored profile " + Utils.getProfileName(params.getProfile().intValue()) + " for device " + params.getDevice());
                return true;
            } catch (RemoteException e) {
                loge("Could not enable profile: " + e);
                return false;
            }
        }
        return false;
    }

    private void tryRemoveRestoredProfileInhibits() {
        HashSet<InhibitRecord> successfullyRemoved = new HashSet<>();
        Iterator<InhibitRecord> it = this.mRestoredInhibits.iterator();
        while (it.hasNext()) {
            InhibitRecord record = it.next();
            if (removeInhibitRecord(record)) {
                successfullyRemoved.add(record);
            }
        }
        this.mRestoredInhibits.removeAll(successfullyRemoved);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeRestoredProfileInhibits() {
        synchronized (this.mProfileInhibitsLock) {
            tryRemoveRestoredProfileInhibits();
            if (!this.mRestoredInhibits.isEmpty()) {
                logd("Could not remove all restored profile inhibits - trying again in 1000ms");
                this.mHandler.postDelayed(new Runnable() { // from class: com.android.car.-$$Lambda$BluetoothProfileInhibitManager$-g2MroqeuYU4pbjS-jgC9o9urXE
                    @Override // java.lang.Runnable
                    public final void run() {
                        BluetoothProfileInhibitManager.this.removeRestoredProfileInhibits();
                    }
                }, RESTORED_PROFILE_INHIBIT_TOKEN, RESTORE_BACKOFF_MILLIS);
            }
        }
    }

    private void releaseAllInhibitsBeforeUnbind() {
        logd("Unbinding CarBluetoothUserService - releasing all profile inhibits");
        synchronized (this.mProfileInhibitsLock) {
            for (BluetoothConnection params : this.mProfileInhibits.keySet()) {
                for (InhibitRecord record : this.mProfileInhibits.get(params)) {
                    record.removeSelf();
                }
            }
            commit();
            this.mProfileInhibits.clear();
            this.mAlreadyDisabledProfiles.clear();
            this.mHandler.removeCallbacksAndMessages(RESTORED_PROFILE_INHIBIT_TOKEN);
            this.mRestoredInhibits.clear();
        }
    }

    private boolean isProxyAvailable(int profile) {
        try {
            return this.mBluetoothUserProxies.isBluetoothConnectionProxyAvailable(profile);
        } catch (RemoteException e) {
            loge("Car BT Service Remote Exception. Proxy for " + Utils.getProfileName(profile) + " not available.");
            return false;
        }
    }

    public void dump(PrintWriter writer, String indent) {
        String inhibits;
        writer.println(indent + TAG + ":");
        writer.println(indent + "\tUser: " + this.mUserId);
        synchronized (this.mProfileInhibitsLock) {
            inhibits = this.mProfileInhibits.keySet().toString();
        }
        writer.println(indent + "\tInhibited profiles: " + inhibits);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logd(String msg) {
        if (DBG) {
            Slog.d(TAG, "[User: " + this.mUserId + "] " + msg);
        }
    }

    private void logw(String msg) {
        Slog.w(TAG, "[User: " + this.mUserId + "] " + msg);
    }

    private void loge(String msg) {
        Slog.e(TAG, "[User: " + this.mUserId + "] " + msg);
    }
}
