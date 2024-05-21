package com.android.car.audio;

import android.media.AudioDeviceInfo;
import android.util.Slog;
import android.view.DisplayAddress;
import com.android.car.CarLog;
import com.android.internal.util.Preconditions;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/* loaded from: classes3.dex */
class CarAudioZone {
    private final int mId;
    private final String mName;
    private final List<CarVolumeGroup> mVolumeGroups = new ArrayList();
    private final List<DisplayAddress.Physical> mPhysicalDisplayAddresses = new ArrayList();

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioZone(int id, String name) {
        this.mId = id;
        this.mName = name;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getId() {
        return this.mId;
    }

    String getName() {
        return this.mName;
    }

    boolean isPrimaryZone() {
        return this.mId == 0;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void addVolumeGroup(CarVolumeGroup volumeGroup) {
        this.mVolumeGroups.add(volumeGroup);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarVolumeGroup getVolumeGroup(int groupId) {
        Preconditions.checkArgumentInRange(groupId, 0, this.mVolumeGroups.size() - 1, "groupId(" + groupId + ") is out of range");
        return this.mVolumeGroups.get(groupId);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public List<AudioDeviceInfo> getAudioDeviceInfos() {
        int[] busNumbers;
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (CarVolumeGroup group : this.mVolumeGroups) {
            for (int busNumber : group.getBusNumbers()) {
                devices.add(group.getCarAudioDeviceInfoForBus(busNumber).getAudioDeviceInfo());
            }
        }
        return devices;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getVolumeGroupCount() {
        return this.mVolumeGroups.size();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void addPhysicalDisplayAddress(DisplayAddress.Physical physicalDisplayAddress) {
        this.mPhysicalDisplayAddresses.add(physicalDisplayAddress);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public List<DisplayAddress.Physical> getPhysicalDisplayAddresses() {
        return this.mPhysicalDisplayAddresses;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarVolumeGroup[] getVolumeGroups() {
        return (CarVolumeGroup[]) this.mVolumeGroups.toArray(new CarVolumeGroup[0]);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean validateVolumeGroups() {
        int[] contexts;
        int[] busNumbers;
        Set<Integer> contextSet = new HashSet<>();
        Set<Integer> busNumberSet = new HashSet<>();
        for (CarVolumeGroup group : this.mVolumeGroups) {
            for (int context : group.getContexts()) {
                if (contextSet.contains(Integer.valueOf(context))) {
                    Slog.e(CarLog.TAG_AUDIO, "Context appears in two groups: " + context);
                    return false;
                }
                Slog.d(CarLog.TAG_AUDIO, "add contextSet: " + context);
                contextSet.add(Integer.valueOf(context));
            }
            for (int busNumber : group.getBusNumbers()) {
                if (busNumberSet.contains(Integer.valueOf(busNumber))) {
                    Slog.e(CarLog.TAG_AUDIO, "Bus appears in two groups: " + busNumber);
                    return false;
                }
                Slog.d(CarLog.TAG_AUDIO, "add busNumberSet:" + busNumber);
                busNumberSet.add(Integer.valueOf(busNumber));
            }
        }
        if (contextSet.size() != CarAudioDynamicRouting.CONTEXT_NUMBERS.length) {
            Slog.e(CarLog.TAG_AUDIO, "Some contexts are not assigned to group");
            Slog.e(CarLog.TAG_AUDIO, "Assigned contexts " + Arrays.toString(contextSet.toArray(new Integer[0])));
            Slog.e(CarLog.TAG_AUDIO, "All contexts " + Arrays.toString(CarAudioDynamicRouting.CONTEXT_NUMBERS));
            return false;
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void synchronizeCurrentGainIndex() {
        for (CarVolumeGroup group : this.mVolumeGroups) {
            group.setCurrentGainIndex(group.getCurrentGainIndex());
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(String indent, PrintWriter writer) {
        writer.printf("%sCarAudioZone(%s:%d) isPrimary? %b\n", indent, this.mName, Integer.valueOf(this.mId), Boolean.valueOf(isPrimaryZone()));
        for (DisplayAddress.Physical physical : this.mPhysicalDisplayAddresses) {
            long port = physical.getPort();
            writer.printf("%sDisplayAddress.Physical(%d)\n", indent + "\t", Long.valueOf(port));
        }
        writer.println();
        for (CarVolumeGroup group : this.mVolumeGroups) {
            group.dump(indent + "\t", writer);
        }
        writer.println();
    }
}
