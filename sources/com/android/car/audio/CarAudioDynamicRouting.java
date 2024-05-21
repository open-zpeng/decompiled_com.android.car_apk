package com.android.car.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.util.Slog;
import android.util.SparseIntArray;
import com.android.car.CarLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;
/* loaded from: classes3.dex */
class CarAudioDynamicRouting {
    static final int DEFAULT_AUDIO_USAGE = 1;
    private final CarAudioZone[] mCarAudioZones;
    static final int[] CONTEXT_NUMBERS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    static final SparseIntArray USAGE_TO_CONTEXT = new SparseIntArray();
    static final int[] STREAM_TYPES = {3, 4, 2};
    static final int[] STREAM_TYPE_USAGES = {1, 4, 6};

    static {
        USAGE_TO_CONTEXT.put(0, 1);
        USAGE_TO_CONTEXT.put(1, 1);
        USAGE_TO_CONTEXT.put(2, 5);
        USAGE_TO_CONTEXT.put(3, 5);
        USAGE_TO_CONTEXT.put(4, 6);
        USAGE_TO_CONTEXT.put(5, 7);
        USAGE_TO_CONTEXT.put(6, 4);
        USAGE_TO_CONTEXT.put(7, 7);
        USAGE_TO_CONTEXT.put(8, 7);
        USAGE_TO_CONTEXT.put(9, 7);
        USAGE_TO_CONTEXT.put(10, 7);
        USAGE_TO_CONTEXT.put(11, 3);
        USAGE_TO_CONTEXT.put(12, 2);
        USAGE_TO_CONTEXT.put(13, 8);
        USAGE_TO_CONTEXT.put(14, 1);
        USAGE_TO_CONTEXT.put(15, 0);
        USAGE_TO_CONTEXT.put(16, 3);
        USAGE_TO_CONTEXT.put(17, 9);
        USAGE_TO_CONTEXT.put(18, 10);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioDynamicRouting(CarAudioZone[] carAudioZones) {
        this.mCarAudioZones = carAudioZones;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setupAudioDynamicRouting(AudioPolicy.Builder builder) {
        CarAudioZone[] carAudioZoneArr;
        CarVolumeGroup[] volumeGroups;
        for (CarAudioZone zone : this.mCarAudioZones) {
            for (CarVolumeGroup group : zone.getVolumeGroups()) {
                setupAudioDynamicRoutingForGroup(group, builder);
            }
        }
    }

    private void setupAudioDynamicRoutingForGroup(CarVolumeGroup group, AudioPolicy.Builder builder) {
        CarVolumeGroup carVolumeGroup = group;
        int[] busNumbers = group.getBusNumbers();
        int length = busNumbers.length;
        int i = 0;
        while (i < length) {
            int busNumber = busNumbers[i];
            CarAudioDeviceInfo info = carVolumeGroup.getCarAudioDeviceInfoForBus(busNumber);
            AudioFormat mixFormat = new AudioFormat.Builder().setSampleRate(info.getSampleRate()).setEncoding(info.getEncodingFormat()).setChannelMask(info.getChannelCount()).build();
            AudioMixingRule.Builder mixingRuleBuilder = new AudioMixingRule.Builder();
            int[] contextsForBus = carVolumeGroup.getContextsForBus(busNumber);
            int length2 = contextsForBus.length;
            boolean hasContext = false;
            int i2 = 0;
            while (i2 < length2) {
                int contextNumber = contextsForBus[i2];
                hasContext = true;
                int[] usages = getUsagesForContext(contextNumber);
                int length3 = usages.length;
                int i3 = 0;
                while (i3 < length3) {
                    int[] iArr = busNumbers;
                    int usage = usages[i3];
                    mixingRuleBuilder.addRule(new AudioAttributes.Builder().setUsage(usage).build(), 1);
                    i3++;
                    busNumbers = iArr;
                    length = length;
                }
                Slog.d(CarLog.TAG_AUDIO, "Bus number: " + busNumber + " contextNumber: " + contextNumber + " sampleRate: " + info.getSampleRate() + " channels: " + info.getChannelCount() + " usages: " + Arrays.toString(usages));
                i2++;
                busNumbers = busNumbers;
            }
            int[] iArr2 = busNumbers;
            int i4 = length;
            if (hasContext) {
                AudioMix audioMix = new AudioMix.Builder(mixingRuleBuilder.build()).setFormat(mixFormat).setDevice(info.getAudioDeviceInfo()).setRouteFlags(1).build();
                builder.addMix(audioMix);
            }
            i++;
            carVolumeGroup = group;
            busNumbers = iArr2;
            length = i4;
        }
    }

    private int[] getUsagesForContext(int contextNumber) {
        List<Integer> usages = new ArrayList<>();
        for (int i = 0; i < USAGE_TO_CONTEXT.size(); i++) {
            if (USAGE_TO_CONTEXT.valueAt(i) == contextNumber) {
                usages.add(Integer.valueOf(USAGE_TO_CONTEXT.keyAt(i)));
            }
        }
        return usages.stream().mapToInt(new ToIntFunction() { // from class: com.android.car.audio.-$$Lambda$CarAudioDynamicRouting$FrUgIaed6Z5vwZ9HGmqoKXbQyP4
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int intValue;
                intValue = ((Integer) obj).intValue();
                return intValue;
            }
        }).toArray();
    }
}
