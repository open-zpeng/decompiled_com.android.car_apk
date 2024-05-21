package com.android.car.audio;

import android.car.media.CarAudioPatchHandle;
import android.car.media.ICarAudio;
import android.car.media.ICarVolumeCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.automotive.audiocontrol.V1_0.IAudioControl;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioFocusInfo;
import android.media.AudioGain;
import android.media.AudioGainConfig;
import android.media.AudioManager;
import android.media.AudioPatch;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioPortConfig;
import android.media.AudioSystem;
import android.media.audiopolicy.AudioPolicy;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.DisplayAddress;
import android.view.KeyEvent;
import androidx.fragment.app.FragmentTransaction;
import androidx.mediarouter.media.SystemMediaRouteProvider;
import com.android.car.BinderInterfaceContainer;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.Manifest;
import com.android.car.R;
import com.android.internal.util.Preconditions;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
public class CarAudioService extends ICarAudio.Stub implements CarServiceBase {
    private static final String VOLUME_SETTINGS_KEY_FOR_GROUP_PREFIX = "android.car.VOLUME_GROUP/";
    private static final String VOLUME_SETTINGS_KEY_MASTER_MUTE = "android.car.MASTER_MUTE";
    private final AudioManager mAudioManager;
    private AudioPolicy mAudioPolicy;
    private String mCarAudioConfigurationPath;
    private CarAudioZone[] mCarAudioZones;
    private final Context mContext;
    private CarZonesAudioFocus mFocusHandler;
    private final boolean mPersistMasterMuteState;
    private final TelephonyManager mTelephonyManager;
    private final boolean mUseDynamicRouting;
    private static boolean sUseCarAudioFocus = false;
    private static final String[] AUDIO_CONFIGURATION_PATHS = {"/vendor/etc/car_audio_configuration.xml", "/system/etc/car_audio_configuration.xml"};
    private final Object mImplLock = new Object();
    private final AudioPolicy.AudioPolicyVolumeCallback mAudioPolicyVolumeCallback = new AudioPolicy.AudioPolicyVolumeCallback() { // from class: com.android.car.audio.CarAudioService.1
        public void onVolumeAdjustment(int adjustment) {
            int usage = CarAudioService.this.getSuggestedAudioUsage();
            Slog.v(CarLog.TAG_AUDIO, "onVolumeAdjustment: " + AudioManager.adjustToString(adjustment) + " suggested usage: " + AudioAttributes.usageToString(usage));
            int groupId = CarAudioService.this.getVolumeGroupIdForUsage(0, usage);
            int currentVolume = CarAudioService.this.getGroupVolume(0, groupId);
            if (adjustment == -100) {
                CarAudioService.this.setMasterMute(true, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                CarAudioService.this.callbackMasterMuteChange(0, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            } else if (adjustment == -1) {
                int maxValue = currentVolume - 1;
                int minValue = Math.max(maxValue, CarAudioService.this.getGroupMinVolume(0, groupId));
                CarAudioService.this.setGroupVolume(0, groupId, minValue, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            } else if (adjustment == 1) {
                int maxValue2 = Math.min(currentVolume + 1, CarAudioService.this.getGroupMaxVolume(0, groupId));
                CarAudioService.this.setGroupVolume(0, groupId, maxValue2, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            } else if (adjustment == 100) {
                CarAudioService.this.setMasterMute(false, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                CarAudioService.this.callbackMasterMuteChange(0, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            } else if (adjustment == 101) {
                CarAudioService carAudioService = CarAudioService.this;
                carAudioService.setMasterMute(true ^ carAudioService.mAudioManager.isMasterMute(), FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                CarAudioService.this.callbackMasterMuteChange(0, FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            }
        }
    };
    private final BinderInterfaceContainer<ICarVolumeCallback> mVolumeCallbackContainer = new BinderInterfaceContainer<>();
    private final BroadcastReceiver mLegacyVolumeChangedReceiver = new BroadcastReceiver() { // from class: com.android.car.audio.CarAudioService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            boolean z;
            String action = intent.getAction();
            int hashCode = action.hashCode();
            if (hashCode != -1940635523) {
                if (hashCode == 1170999219 && action.equals("android.media.MASTER_MUTE_CHANGED_ACTION")) {
                    z = true;
                }
                z = true;
            } else {
                if (action.equals(SystemMediaRouteProvider.LegacyImpl.VolumeChangeReceiver.VOLUME_CHANGED_ACTION)) {
                    z = false;
                }
                z = true;
            }
            if (z) {
                if (z) {
                    CarAudioService.this.callbackMasterMuteChange(0, 0);
                    return;
                }
                return;
            }
            int streamType = intent.getIntExtra(SystemMediaRouteProvider.LegacyImpl.VolumeChangeReceiver.EXTRA_VOLUME_STREAM_TYPE, -1);
            int groupId = CarAudioService.this.getVolumeGroupIdForStreamType(streamType);
            if (groupId != -1) {
                CarAudioService.this.callbackGroupVolumeChange(0, groupId, 0);
                return;
            }
            Slog.w(CarLog.TAG_AUDIO, "Unknown stream type: " + streamType);
        }
    };
    private Map<Integer, Integer> mUidToZoneMap = new HashMap();

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String getVolumeSettingsKeyForGroup(int zoneId, int groupId) {
        int maskedGroupId = (zoneId << 8) + groupId;
        return VOLUME_SETTINGS_KEY_FOR_GROUP_PREFIX + maskedGroupId;
    }

    public CarAudioService(Context context) {
        this.mContext = context;
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mAudioManager = (AudioManager) this.mContext.getSystemService("audio");
        this.mUseDynamicRouting = this.mContext.getResources().getBoolean(R.bool.audioUseDynamicRouting);
        this.mPersistMasterMuteState = this.mContext.getResources().getBoolean(R.bool.audioPersistMasterMuteState);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        synchronized (this.mImplLock) {
            boolean z = true;
            if (this.mUseDynamicRouting) {
                AudioDeviceInfo[] deviceInfos = this.mAudioManager.getDevices(2);
                if (deviceInfos.length == 0) {
                    Slog.e(CarLog.TAG_AUDIO, "No output device available, ignore");
                    return;
                }
                SparseArray<CarAudioDeviceInfo> busToCarAudioDeviceInfo = new SparseArray<>();
                for (AudioDeviceInfo info : deviceInfos) {
                    Slog.v(CarLog.TAG_AUDIO, String.format("output id=%d address=%s type=%s", Integer.valueOf(info.getId()), info.getAddress(), Integer.valueOf(info.getType())));
                    if (info.getType() == 21) {
                        CarAudioDeviceInfo carInfo = new CarAudioDeviceInfo(info);
                        if (carInfo.getBusNumber() >= 0) {
                            busToCarAudioDeviceInfo.put(carInfo.getBusNumber(), carInfo);
                            Slog.i(CarLog.TAG_AUDIO, "Valid bus found " + carInfo);
                        }
                    }
                }
                setupDynamicRouting(busToCarAudioDeviceInfo);
            } else {
                Slog.i(CarLog.TAG_AUDIO, "Audio dynamic routing not enabled, run in legacy mode");
                setupLegacyVolumeChangedListener();
            }
            if (this.mPersistMasterMuteState) {
                if (Settings.Global.getInt(this.mContext.getContentResolver(), VOLUME_SETTINGS_KEY_MASTER_MUTE, 0) == 0) {
                    z = false;
                }
                boolean storedMasterMute = z;
                setMasterMute(storedMasterMute, 0);
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        synchronized (this.mImplLock) {
            if (this.mUseDynamicRouting) {
                if (this.mAudioPolicy != null) {
                    this.mAudioManager.unregisterAudioPolicyAsync(this.mAudioPolicy);
                    this.mAudioPolicy = null;
                    this.mFocusHandler.setOwningPolicy(null, null);
                    this.mFocusHandler = null;
                }
            } else {
                this.mContext.unregisterReceiver(this.mLegacyVolumeChangedReceiver);
            }
            this.mVolumeCallbackContainer.clear();
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        CarAudioZone[] carAudioZoneArr;
        writer.println("*CarAudioService*");
        StringBuilder sb = new StringBuilder();
        sb.append("\tRun in legacy mode? ");
        sb.append(!this.mUseDynamicRouting);
        writer.println(sb.toString());
        writer.println("\tPersist master mute state? " + this.mPersistMasterMuteState);
        writer.println("\tMaster muted? " + this.mAudioManager.isMasterMute());
        if (this.mCarAudioConfigurationPath != null) {
            writer.println("\tCar audio configuration path: " + this.mCarAudioConfigurationPath);
        }
        writer.println();
        if (this.mUseDynamicRouting) {
            for (CarAudioZone zone : this.mCarAudioZones) {
                zone.dump("\t", writer);
            }
            writer.println();
            writer.println("\tUID to Zone Mapping:");
            for (Integer num : this.mUidToZoneMap.keySet()) {
                int callingId = num.intValue();
                writer.printf("\t\tUID %d mapped to zone %d\n", Integer.valueOf(callingId), this.mUidToZoneMap.get(Integer.valueOf(callingId)));
            }
            writer.println();
            this.mFocusHandler.dump("\t", writer);
        }
    }

    public boolean isDynamicRoutingEnabled() {
        return this.mUseDynamicRouting;
    }

    public void setGroupVolume(int zoneId, int groupId, int index, int flags) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            callbackGroupVolumeChange(zoneId, groupId, flags);
            if (!this.mUseDynamicRouting) {
                this.mAudioManager.setStreamVolume(CarAudioDynamicRouting.STREAM_TYPES[groupId], index, flags);
                return;
            }
            CarVolumeGroup group = getCarVolumeGroup(zoneId, groupId);
            group.setCurrentGainIndex(index);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void callbackGroupVolumeChange(int zoneId, int groupId, int flags) {
        for (BinderInterfaceContainer.BinderInterface<ICarVolumeCallback> callback : this.mVolumeCallbackContainer.getInterfaces()) {
            try {
                callback.binderInterface.onGroupVolumeChanged(zoneId, groupId, flags);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_AUDIO, "Failed to callback onGroupVolumeChanged", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setMasterMute(boolean mute, int flags) {
        this.mAudioManager.setMasterMute(mute, flags);
        int keycode = mute ? 127 : 126;
        this.mAudioManager.dispatchMediaKeyEvent(new KeyEvent(0, keycode));
        this.mAudioManager.dispatchMediaKeyEvent(new KeyEvent(1, keycode));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void callbackMasterMuteChange(int zoneId, int flags) {
        for (BinderInterfaceContainer.BinderInterface<ICarVolumeCallback> callback : this.mVolumeCallbackContainer.getInterfaces()) {
            try {
                callback.binderInterface.onMasterMuteChanged(zoneId, flags);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_AUDIO, "Failed to callback onMasterMuteChanged", e);
            }
        }
        if (this.mPersistMasterMuteState) {
            Settings.Global.putInt(this.mContext.getContentResolver(), VOLUME_SETTINGS_KEY_MASTER_MUTE, this.mAudioManager.isMasterMute() ? 1 : 0);
        }
    }

    public int getGroupMaxVolume(int zoneId, int groupId) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            if (!this.mUseDynamicRouting) {
                return this.mAudioManager.getStreamMaxVolume(CarAudioDynamicRouting.STREAM_TYPES[groupId]);
            }
            CarVolumeGroup group = getCarVolumeGroup(zoneId, groupId);
            return group.getMaxGainIndex();
        }
    }

    public int getGroupMinVolume(int zoneId, int groupId) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            if (!this.mUseDynamicRouting) {
                return this.mAudioManager.getStreamMinVolume(CarAudioDynamicRouting.STREAM_TYPES[groupId]);
            }
            CarVolumeGroup group = getCarVolumeGroup(zoneId, groupId);
            return group.getMinGainIndex();
        }
    }

    public int getGroupVolume(int zoneId, int groupId) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            if (!this.mUseDynamicRouting) {
                return this.mAudioManager.getStreamVolume(CarAudioDynamicRouting.STREAM_TYPES[groupId]);
            }
            CarVolumeGroup group = getCarVolumeGroup(zoneId, groupId);
            return group.getCurrentGainIndex();
        }
    }

    private CarVolumeGroup getCarVolumeGroup(int zoneId, int groupId) {
        Preconditions.checkNotNull(this.mCarAudioZones);
        Preconditions.checkArgumentInRange(zoneId, 0, this.mCarAudioZones.length - 1, "zoneId out of range: " + zoneId);
        return this.mCarAudioZones[zoneId].getVolumeGroup(groupId);
    }

    private void setupLegacyVolumeChangedListener() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SystemMediaRouteProvider.LegacyImpl.VolumeChangeReceiver.VOLUME_CHANGED_ACTION);
        intentFilter.addAction("android.media.MASTER_MUTE_CHANGED_ACTION");
        this.mContext.registerReceiver(this.mLegacyVolumeChangedReceiver, intentFilter);
    }

    private void setupDynamicRouting(SparseArray<CarAudioDeviceInfo> busToCarAudioDeviceInfo) {
        CarAudioZone[] carAudioZoneArr;
        AudioPolicy.Builder builder = new AudioPolicy.Builder(this.mContext);
        builder.setLooper(Looper.getMainLooper());
        this.mCarAudioConfigurationPath = getAudioConfigurationPath();
        String str = this.mCarAudioConfigurationPath;
        if (str != null) {
            try {
                InputStream inputStream = new FileInputStream(str);
                CarAudioZonesHelper zonesHelper = new CarAudioZonesHelper(this.mContext, inputStream, busToCarAudioDeviceInfo);
                this.mCarAudioZones = zonesHelper.loadAudioZones();
                inputStream.close();
            } catch (IOException | XmlPullParserException e) {
                throw new RuntimeException("Failed to parse audio zone configuration", e);
            }
        } else {
            IAudioControl audioControl = getAudioControl();
            if (audioControl == null) {
                throw new RuntimeException("Dynamic routing requested but audioControl HAL not available");
            }
            CarAudioZonesHelperLegacy legacyHelper = new CarAudioZonesHelperLegacy(this.mContext, R.xml.car_volume_groups, busToCarAudioDeviceInfo, audioControl);
            this.mCarAudioZones = legacyHelper.loadAudioZones();
        }
        for (CarAudioZone zone : this.mCarAudioZones) {
            if (!zone.validateVolumeGroups()) {
                throw new RuntimeException("Invalid volume groups configuration");
            }
            zone.synchronizeCurrentGainIndex();
            Slog.v(CarLog.TAG_AUDIO, "Processed audio zone: " + zone);
        }
        CarAudioDynamicRouting dynamicRouting = new CarAudioDynamicRouting(this.mCarAudioZones);
        dynamicRouting.setupAudioDynamicRouting(builder);
        builder.setAudioPolicyVolumeCallback(this.mAudioPolicyVolumeCallback);
        if (sUseCarAudioFocus) {
            this.mFocusHandler = new CarZonesAudioFocus(this.mAudioManager, this.mContext.getPackageManager(), this.mCarAudioZones);
            builder.setAudioPolicyFocusListener(this.mFocusHandler);
            builder.setIsAudioFocusPolicy(true);
        }
        this.mAudioPolicy = builder.build();
        if (sUseCarAudioFocus) {
            this.mFocusHandler.setOwningPolicy(this, this.mAudioPolicy);
        }
        int r = this.mAudioManager.registerAudioPolicy(this.mAudioPolicy);
        if (r != 0) {
            throw new RuntimeException("registerAudioPolicy failed " + r);
        }
    }

    private String getAudioConfigurationPath() {
        String[] strArr;
        for (String path : AUDIO_CONFIGURATION_PATHS) {
            File configuration = new File(path);
            if (configuration.exists()) {
                return path;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getContextForUsage(int audioUsage) {
        return CarAudioDynamicRouting.USAGE_TO_CONTEXT.get(audioUsage);
    }

    public void setFadeTowardFront(float value) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            IAudioControl audioControlHal = getAudioControl();
            if (audioControlHal != null) {
                try {
                    audioControlHal.setFadeTowardFront(value);
                } catch (RemoteException e) {
                    Slog.e(CarLog.TAG_AUDIO, "setFadeTowardFront failed", e);
                }
            }
        }
    }

    public void setBalanceTowardRight(float value) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            IAudioControl audioControlHal = getAudioControl();
            if (audioControlHal != null) {
                try {
                    audioControlHal.setBalanceTowardRight(value);
                } catch (RemoteException e) {
                    Slog.e(CarLog.TAG_AUDIO, "setBalanceTowardRight failed", e);
                }
            }
        }
    }

    public String[] getExternalSources() {
        String[] strArr;
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
            List<String> sourceAddresses = new ArrayList<>();
            AudioDeviceInfo[] devices = this.mAudioManager.getDevices(1);
            if (devices.length == 0) {
                Slog.w(CarLog.TAG_AUDIO, "getExternalSources, no input devices found.");
            }
            for (AudioDeviceInfo info : devices) {
                switch (info.getType()) {
                    case 5:
                    case 6:
                    case 9:
                    case 11:
                    case 12:
                    case 14:
                    case 16:
                    case 17:
                    case 19:
                    case 20:
                    case 21:
                    case 22:
                        String address = info.getAddress();
                        if (TextUtils.isEmpty(address)) {
                            Slog.w(CarLog.TAG_AUDIO, "Discarded device with empty address, type=" + info.getType());
                            break;
                        } else {
                            sourceAddresses.add(address);
                            break;
                        }
                }
            }
            strArr = (String[]) sourceAddresses.toArray(new String[0]);
        }
        return strArr;
    }

    public CarAudioPatchHandle createAudioPatch(String sourceAddress, int usage, int gainInMillibels) {
        CarAudioPatchHandle createAudioPatchLocked;
        Log.d("CarAudioService", "createAudioPatch  " + Binder.getCallingPid());
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
            createAudioPatchLocked = createAudioPatchLocked(sourceAddress, usage, gainInMillibels);
        }
        return createAudioPatchLocked;
    }

    public void releaseAudioPatch(CarAudioPatchHandle carPatch) {
        Log.d("CarAudioService", "releaseAudioPatch  " + Binder.getCallingPid());
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
            releaseAudioPatchLocked(carPatch);
        }
    }

    private CarAudioPatchHandle createAudioPatchLocked(String sourceAddress, int usage, int gainInMillibels) {
        AudioDeviceInfo sourcePortInfo = null;
        AudioDeviceInfo[] deviceInfos = this.mAudioManager.getDevices(1);
        int length = deviceInfos.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                break;
            }
            AudioDeviceInfo info = deviceInfos[i];
            if (!sourceAddress.equals(info.getAddress())) {
                i++;
            } else {
                sourcePortInfo = info;
                break;
            }
        }
        Preconditions.checkNotNull(sourcePortInfo, "Specified source is not available: " + sourceAddress);
        AudioDevicePort sinkPort = (AudioDevicePort) Preconditions.checkNotNull(getAudioPort(usage), "Sink not available for usage: " + AudioAttributes.usageToString(usage));
        AudioPortConfig sinkConfig = sinkPort.buildConfig(0, 1, 1, (AudioGainConfig) null);
        Slog.d(CarLog.TAG_AUDIO, "createAudioPatch sinkConfig: " + sinkConfig);
        CarAudioDeviceInfo helper = new CarAudioDeviceInfo(sourcePortInfo);
        AudioGain audioGain = (AudioGain) Preconditions.checkNotNull(helper.getAudioGain(), "Gain controller not available for source port");
        AudioGainConfig audioGainConfig = audioGain.buildConfig(1, audioGain.channelMask(), new int[]{gainInMillibels}, 0);
        AudioPortConfig sourceConfig = sourcePortInfo.getPort().buildConfig(0, 1, 1, audioGainConfig);
        AudioPatch[] patch = {null};
        int result = AudioManager.createAudioPatch(patch, new AudioPortConfig[]{sourceConfig}, new AudioPortConfig[]{sinkConfig});
        if (result != 0) {
            throw new RuntimeException("createAudioPatch failed with code " + result);
        }
        Preconditions.checkNotNull(patch[0], "createAudioPatch didn't provide expected single handle");
        Slog.d(CarLog.TAG_AUDIO, "Audio patch created: " + patch[0]);
        int groupId = getVolumeGroupIdForUsage(0, usage);
        setGroupVolume(0, groupId, getGroupVolume(0, groupId), 0);
        return new CarAudioPatchHandle(patch[0]);
    }

    private void releaseAudioPatchLocked(CarAudioPatchHandle carPatch) {
        ArrayList<AudioPatch> patches = new ArrayList<>();
        int result = AudioSystem.listAudioPatches(patches, new int[1]);
        if (result != 0) {
            throw new RuntimeException("listAudioPatches failed with code " + result);
        }
        Iterator<AudioPatch> it = patches.iterator();
        while (it.hasNext()) {
            AudioPatch patch = it.next();
            if (carPatch.represents(patch)) {
                int result2 = AudioManager.releaseAudioPatch(patch);
                if (result2 != 0) {
                    throw new RuntimeException("releaseAudioPatch failed with code " + result2);
                }
                return;
            }
        }
        Slog.e(CarLog.TAG_AUDIO, "releaseAudioPatch found no match for " + carPatch);
    }

    public int getVolumeGroupCount(int zoneId) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            Preconditions.checkNotNull(this.mCarAudioZones);
            if (!this.mUseDynamicRouting) {
                return CarAudioDynamicRouting.STREAM_TYPES.length;
            }
            Preconditions.checkArgumentInRange(zoneId, 0, this.mCarAudioZones.length - 1, "zoneId out of range: " + zoneId);
            return this.mCarAudioZones[zoneId].getVolumeGroupCount();
        }
    }

    public int getVolumeGroupIdForUsage(int zoneId, int usage) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            Preconditions.checkNotNull(this.mCarAudioZones);
            Preconditions.checkArgumentInRange(zoneId, 0, this.mCarAudioZones.length - 1, "zoneId out of range: " + zoneId);
            CarVolumeGroup[] groups = this.mCarAudioZones[zoneId].getVolumeGroups();
            for (int i = 0; i < groups.length; i++) {
                int[] contexts = groups[i].getContexts();
                for (int context : contexts) {
                    if (getContextForUsage(usage) == context) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    public int[] getUsagesForVolumeGroupId(int zoneId, int groupId) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            if (!this.mUseDynamicRouting) {
                return new int[]{CarAudioDynamicRouting.STREAM_TYPE_USAGES[groupId]};
            }
            CarVolumeGroup group = getCarVolumeGroup(zoneId, groupId);
            Set<Integer> contexts = (Set) Arrays.stream(group.getContexts()).boxed().collect(Collectors.toSet());
            List<Integer> usages = new ArrayList<>();
            for (int i = 0; i < CarAudioDynamicRouting.USAGE_TO_CONTEXT.size(); i++) {
                if (contexts.contains(Integer.valueOf(CarAudioDynamicRouting.USAGE_TO_CONTEXT.valueAt(i)))) {
                    usages.add(Integer.valueOf(CarAudioDynamicRouting.USAGE_TO_CONTEXT.keyAt(i)));
                }
            }
            return usages.stream().mapToInt(new ToIntFunction() { // from class: com.android.car.audio.-$$Lambda$CarAudioService$7jfwuw0AKCFjAyMehOdgt6SmieI
                @Override // java.util.function.ToIntFunction
                public final int applyAsInt(Object obj) {
                    int intValue;
                    intValue = ((Integer) obj).intValue();
                    return intValue;
                }
            }).toArray();
        }
    }

    public int[] getAudioZoneIds() {
        int[] array;
        enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (this.mImplLock) {
            Preconditions.checkNotNull(this.mCarAudioZones);
            array = Arrays.stream(this.mCarAudioZones).mapToInt(new ToIntFunction() { // from class: com.android.car.audio.-$$Lambda$A6-s85SDLQPJZMlz96mntxABBu0
                @Override // java.util.function.ToIntFunction
                public final int applyAsInt(Object obj) {
                    return ((CarAudioZone) obj).getId();
                }
            }).toArray();
        }
        return array;
    }

    public int getZoneIdForUid(int uid) {
        int intValue;
        enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (this.mImplLock) {
            if (!this.mUidToZoneMap.containsKey(Integer.valueOf(uid))) {
                Slog.i(CarLog.TAG_AUDIO, "getZoneIdForUid uid " + uid + " does not have a zone. Defaulting to PRIMARY_AUDIO_ZONE: 0");
                setZoneIdForUidNoCheckLocked(0, uid);
            }
            intValue = this.mUidToZoneMap.get(Integer.valueOf(uid)).intValue();
        }
        return intValue;
    }

    public boolean setZoneIdForUid(int zoneId, int uid) {
        enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (this.mImplLock) {
            Slog.i(CarLog.TAG_AUDIO, "setZoneIdForUid Calling uid " + uid + " mapped to : " + zoneId);
            Integer currentZoneId = this.mUidToZoneMap.get(Integer.valueOf(uid));
            ArrayList<AudioFocusInfo> currentFocusHoldersForUid = new ArrayList<>();
            ArrayList<AudioFocusInfo> currentFocusLosersForUid = new ArrayList<>();
            if (currentZoneId != null) {
                currentFocusHoldersForUid = this.mFocusHandler.getAudioFocusHoldersForUid(uid, currentZoneId.intValue());
                currentFocusLosersForUid = this.mFocusHandler.getAudioFocusLosersForUid(uid, currentZoneId.intValue());
                if (!currentFocusHoldersForUid.isEmpty() || !currentFocusLosersForUid.isEmpty()) {
                    this.mFocusHandler.transientlyLoseInFocusInZone(currentFocusLosersForUid, currentZoneId.intValue());
                    this.mFocusHandler.transientlyLoseInFocusInZone(currentFocusHoldersForUid, currentZoneId.intValue());
                }
            }
            if (checkAndRemoveUidLocked(uid) && setZoneIdForUidNoCheckLocked(zoneId, uid)) {
                if (!currentFocusLosersForUid.isEmpty()) {
                    regainAudioFocusLocked(currentFocusLosersForUid, zoneId);
                }
                if (!currentFocusHoldersForUid.isEmpty()) {
                    regainAudioFocusLocked(currentFocusHoldersForUid, zoneId);
                }
                return true;
            }
            return false;
        }
    }

    void regainAudioFocusLocked(ArrayList<AudioFocusInfo> afiList, int zoneId) {
        Iterator<AudioFocusInfo> it = afiList.iterator();
        while (it.hasNext()) {
            AudioFocusInfo info = it.next();
            if (this.mFocusHandler.reevaluateAndRegainAudioFocus(info) != 1) {
                Slog.i(CarLog.TAG_AUDIO, " Focus could not be granted for entry " + info.getClientId() + " uid " + info.getClientUid() + " in zone " + zoneId);
            }
        }
    }

    public boolean clearZoneIdForUid(int uid) {
        boolean checkAndRemoveUidLocked;
        enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (this.mImplLock) {
            checkAndRemoveUidLocked = checkAndRemoveUidLocked(uid);
        }
        return checkAndRemoveUidLocked;
    }

    private boolean setZoneIdForUidNoCheckLocked(int zoneId, int uid) {
        Slog.d(CarLog.TAG_AUDIO, "setZoneIdForUidNoCheck Calling uid " + uid + " mapped to " + zoneId);
        Preconditions.checkNotNull(this.mCarAudioZones);
        if (this.mAudioPolicy.setUidDeviceAffinity(uid, this.mCarAudioZones[zoneId].getAudioDeviceInfos())) {
            this.mUidToZoneMap.put(Integer.valueOf(uid), Integer.valueOf(zoneId));
            return true;
        }
        Slog.w(CarLog.TAG_AUDIO, "setZoneIdForUidNoCheck Failed set device affinity for uid " + uid + " in zone " + zoneId);
        return false;
    }

    private boolean checkAndRemoveUidLocked(int uid) {
        Integer zoneId = this.mUidToZoneMap.get(Integer.valueOf(uid));
        if (zoneId == null) {
            return true;
        }
        Slog.i(CarLog.TAG_AUDIO, "checkAndRemoveUid removing Calling uid " + uid + " from zone " + zoneId);
        if (this.mAudioPolicy.removeUidDeviceAffinity(uid)) {
            this.mUidToZoneMap.remove(Integer.valueOf(uid));
            return true;
        }
        Slog.w(CarLog.TAG_AUDIO, "checkAndRemoveUid Failed remove device affinity for uid " + uid + " in zone " + zoneId);
        return false;
    }

    public int getZoneIdForDisplayPortId(final byte displayPortId) {
        enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_SETTINGS);
        synchronized (this.mImplLock) {
            Preconditions.checkNotNull(this.mCarAudioZones);
            for (int index = 0; index < this.mCarAudioZones.length; index++) {
                CarAudioZone zone = this.mCarAudioZones[index];
                List<DisplayAddress.Physical> displayAddresses = zone.getPhysicalDisplayAddresses();
                if (displayAddresses.stream().anyMatch(new Predicate() { // from class: com.android.car.audio.-$$Lambda$CarAudioService$g5877h_ygc54gR2giA7qVhrBkGo
                    @Override // java.util.function.Predicate
                    public final boolean test(Object obj) {
                        return CarAudioService.lambda$getZoneIdForDisplayPortId$1(displayPortId, (DisplayAddress.Physical) obj);
                    }
                })) {
                    return index;
                }
            }
            return 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$getZoneIdForDisplayPortId$1(byte displayPortId, DisplayAddress.Physical displayAddress) {
        return displayAddress.getPort() == displayPortId;
    }

    public void registerVolumeCallback(IBinder binder) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            this.mVolumeCallbackContainer.addBinder(ICarVolumeCallback.Stub.asInterface(binder));
        }
    }

    public void unregisterVolumeCallback(IBinder binder) {
        synchronized (this.mImplLock) {
            enforcePermission(Manifest.permission.CAR_CONTROL_AUDIO_VOLUME);
            this.mVolumeCallbackContainer.removeBinder(ICarVolumeCallback.Stub.asInterface(binder));
        }
    }

    private void enforcePermission(String permissionName) {
        if (this.mContext.checkCallingOrSelfPermission(permissionName) != 0) {
            throw new SecurityException("requires permission " + permissionName);
        }
    }

    private AudioDevicePort getAudioPort(int usage) {
        int groupId = getVolumeGroupIdForUsage(0, usage);
        Preconditions.checkNotNull(this.mCarAudioZones);
        CarVolumeGroup volumeGroup = this.mCarAudioZones[0].getVolumeGroup(groupId);
        CarVolumeGroup group = (CarVolumeGroup) Preconditions.checkNotNull(volumeGroup, "Can not find CarVolumeGroup by usage: " + AudioAttributes.usageToString(usage));
        return group.getAudioDevicePortForContext(getContextForUsage(usage));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getSuggestedAudioUsage() {
        int callState = this.mTelephonyManager.getCallState();
        if (callState == 1) {
            return 6;
        }
        if (callState == 2) {
            return 2;
        }
        List<AudioPlaybackConfiguration> playbacks = (List) this.mAudioManager.getActivePlaybackConfigurations().stream().filter(new Predicate() { // from class: com.android.car.audio.-$$Lambda$GmcZA0zXB8mu4enjpmQnD7hZdQI
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return ((AudioPlaybackConfiguration) obj).isActive();
            }
        }).collect(Collectors.toList());
        if (!playbacks.isEmpty()) {
            return playbacks.get(playbacks.size() - 1).getAudioAttributes().getUsage();
        }
        return 1;
    }

    public int getVolumeGroupIdForStreamType(int streamType) {
        int usage = getUsageForStreamType(streamType);
        if (usage < 0) {
            return -1;
        }
        int groupId = getVolumeGroupIdForUsage(0, usage);
        return groupId;
    }

    private int getUsageForStreamType(int streamType) {
        switch (streamType) {
            case 0:
            case 2:
                return 2;
            case 1:
            case 4:
            case 5:
            case 7:
            case 8:
                return 5;
            case 3:
                return 1;
            case 6:
                return 2;
            case 9:
                return 12;
            case 10:
                return 2;
            default:
                return -1;
        }
    }

    private static IAudioControl getAudioControl() {
        try {
            return IAudioControl.getService();
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_AUDIO, "Failed to get IAudioControl service", e);
            return null;
        } catch (NoSuchElementException e2) {
            Slog.e(CarLog.TAG_AUDIO, "IAudioControl service not registered yet");
            return null;
        }
    }

    public boolean getAudioStatus() {
        return AudioSystem.isStreamActive(0, 0) || AudioSystem.isStreamActive(1, 0) || AudioSystem.isStreamActive(2, 0) || AudioSystem.isStreamActive(3, 0) || AudioSystem.isStreamActive(4, 0) || AudioSystem.isStreamActive(5, 0) || AudioSystem.isStreamActive(6, 0) || AudioSystem.isStreamActive(7, 0) || AudioSystem.isStreamActive(8, 0) || AudioSystem.isStreamActive(9, 0);
    }
}
