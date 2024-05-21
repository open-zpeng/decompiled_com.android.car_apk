package com.android.car.audio;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.automotive.audiocontrol.V1_0.ContextNumber;
import android.media.AudioDevicePort;
import android.media.AudioGain;
import android.provider.Settings;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.android.internal.util.Preconditions;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public final class CarVolumeGroup {
    private final SparseArray<CarAudioDeviceInfo> mBusToCarAudioDeviceInfo;
    private final ContentResolver mContentResolver;
    private final SparseIntArray mContextToBus;
    private int mCurrentGainIndex;
    private int mDefaultGain;
    private final int mId;
    private int mMaxGain;
    private int mMinGain;
    private int mStepSize;
    private int mStoredGainIndex;
    private final int mZoneId;

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarVolumeGroup(Context context, int zoneId, int id) {
        this.mContextToBus = new SparseIntArray();
        this.mBusToCarAudioDeviceInfo = new SparseArray<>();
        this.mDefaultGain = Integer.MIN_VALUE;
        this.mMaxGain = Integer.MIN_VALUE;
        this.mMinGain = Integer.MAX_VALUE;
        this.mStepSize = 0;
        this.mCurrentGainIndex = -1;
        this.mContentResolver = context.getContentResolver();
        this.mZoneId = zoneId;
        this.mId = id;
        this.mStoredGainIndex = Settings.Global.getInt(this.mContentResolver, CarAudioService.getVolumeSettingsKeyForGroup(this.mZoneId, this.mId), -1);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Deprecated
    public CarVolumeGroup(Context context, int zoneId, int id, int[] contexts) {
        this(context, zoneId, id);
        for (int audioContext : contexts) {
            this.mContextToBus.put(audioContext, -1);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioDeviceInfo getCarAudioDeviceInfoForBus(int busNumber) {
        return this.mBusToCarAudioDeviceInfo.get(busNumber);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int[] getContexts() {
        int[] contextNumbers = new int[this.mContextToBus.size()];
        for (int i = 0; i < contextNumbers.length; i++) {
            contextNumbers[i] = this.mContextToBus.keyAt(i);
        }
        return contextNumbers;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int[] getContextsForBus(int busNumber) {
        List<Integer> contextNumbers = new ArrayList<>();
        for (int i = 0; i < this.mContextToBus.size(); i++) {
            int value = this.mContextToBus.valueAt(i);
            if (value == busNumber) {
                contextNumbers.add(Integer.valueOf(this.mContextToBus.keyAt(i)));
            }
        }
        return contextNumbers.stream().mapToInt(new ToIntFunction() { // from class: com.android.car.audio.-$$Lambda$CarVolumeGroup$azkpj2FcIJoM7Mcs4-9AI3-iB1E
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int intValue;
                intValue = ((Integer) obj).intValue();
                return intValue;
            }
        }).toArray();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int[] getBusNumbers() {
        int[] busNumbers = new int[this.mBusToCarAudioDeviceInfo.size()];
        for (int i = 0; i < busNumbers.length; i++) {
            busNumbers[i] = this.mBusToCarAudioDeviceInfo.keyAt(i);
        }
        return busNumbers;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void bind(int contextNumber, int busNumber, CarAudioDeviceInfo info) {
        AudioGain audioGain = info.getAudioGain();
        Preconditions.checkNotNull(audioGain);
        if (this.mBusToCarAudioDeviceInfo.size() == 0) {
            this.mStepSize = audioGain.stepValue();
        } else {
            Preconditions.checkArgument(audioGain.stepValue() == this.mStepSize, "Gain controls within one group must have same step value");
        }
        this.mContextToBus.put(contextNumber, busNumber);
        this.mBusToCarAudioDeviceInfo.put(busNumber, info);
        if (info.getDefaultGain() > this.mDefaultGain) {
            this.mDefaultGain = info.getDefaultGain();
        }
        if (info.getMaxGain() > this.mMaxGain) {
            this.mMaxGain = info.getMaxGain();
        }
        if (info.getMinGain() < this.mMinGain) {
            this.mMinGain = info.getMinGain();
        }
        if (this.mStoredGainIndex < getMinGainIndex() || this.mStoredGainIndex > getMaxGainIndex()) {
            this.mCurrentGainIndex = getIndexForGain(this.mDefaultGain);
        } else {
            this.mCurrentGainIndex = this.mStoredGainIndex;
        }
    }

    private int getDefaultGainIndex() {
        return getIndexForGain(this.mDefaultGain);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getMaxGainIndex() {
        return getIndexForGain(this.mMaxGain);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getMinGainIndex() {
        return getIndexForGain(this.mMinGain);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getCurrentGainIndex() {
        return this.mCurrentGainIndex;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setCurrentGainIndex(int gainIndex) {
        int gainInMillibels = getGainForIndex(gainIndex);
        boolean z = gainInMillibels >= this.mMinGain && gainInMillibels <= this.mMaxGain;
        Preconditions.checkArgument(z, "Gain out of range (" + this.mMinGain + ":" + this.mMaxGain + ") " + gainInMillibels + "index " + gainIndex);
        for (int i = 0; i < this.mBusToCarAudioDeviceInfo.size(); i++) {
            CarAudioDeviceInfo info = this.mBusToCarAudioDeviceInfo.valueAt(i);
            info.setCurrentGain(gainInMillibels);
        }
        this.mCurrentGainIndex = gainIndex;
        Settings.Global.putInt(this.mContentResolver, CarAudioService.getVolumeSettingsKeyForGroup(this.mZoneId, this.mId), gainIndex);
    }

    private int getGainForIndex(int gainIndex) {
        return this.mMinGain + (this.mStepSize * gainIndex);
    }

    private int getIndexForGain(int gainInMillibel) {
        return (gainInMillibel - this.mMinGain) / this.mStepSize;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public AudioDevicePort getAudioDevicePortForContext(int contextNumber) {
        int busNumber = this.mContextToBus.get(contextNumber, -1);
        if (busNumber < 0 || this.mBusToCarAudioDeviceInfo.get(busNumber) == null) {
            return null;
        }
        return this.mBusToCarAudioDeviceInfo.get(busNumber).getAudioDevicePort();
    }

    public String toString() {
        return "CarVolumeGroup id: " + this.mId + " currentGainIndex: " + this.mCurrentGainIndex + " contexts: " + Arrays.toString(getContexts()) + " buses: " + Arrays.toString(getBusNumbers());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(String indent, PrintWriter writer) {
        writer.printf("%sCarVolumeGroup(%d)\n", indent, Integer.valueOf(this.mId));
        writer.printf("%sGain values (min / max / default/ current): %d %d %d %d\n", indent, Integer.valueOf(this.mMinGain), Integer.valueOf(this.mMaxGain), Integer.valueOf(this.mDefaultGain), Integer.valueOf(getGainForIndex(this.mCurrentGainIndex)));
        writer.printf("%sGain indexes (min / max / default / current): %d %d %d %d\n", indent, Integer.valueOf(getMinGainIndex()), Integer.valueOf(getMaxGainIndex()), Integer.valueOf(getDefaultGainIndex()), Integer.valueOf(this.mCurrentGainIndex));
        for (int i = 0; i < this.mContextToBus.size(); i++) {
            writer.printf("%sContext: %s -> Bus: %d\n", indent, ContextNumber.toString(this.mContextToBus.keyAt(i)), Integer.valueOf(this.mContextToBus.valueAt(i)));
        }
        for (int i2 = 0; i2 < this.mBusToCarAudioDeviceInfo.size(); i2++) {
            this.mBusToCarAudioDeviceInfo.valueAt(i2).dump(indent, writer);
        }
        writer.println();
    }
}
