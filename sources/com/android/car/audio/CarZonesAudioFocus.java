package com.android.car.audio;

import android.content.pm.PackageManager;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.os.Bundle;
import android.util.Slog;
import com.android.car.CarLog;
import com.android.internal.util.Preconditions;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntFunction;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class CarZonesAudioFocus extends AudioPolicy.AudioPolicyFocusListener {
    private AudioPolicy mAudioPolicy;
    private CarAudioService mCarAudioService;
    private final Map<Integer, CarAudioFocus> mFocusZones = new HashMap();

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarZonesAudioFocus(AudioManager audioManager, PackageManager packageManager, CarAudioZone[] carAudioZones) {
        Preconditions.checkNotNull(carAudioZones);
        Preconditions.checkArgument(carAudioZones.length != 0, "There must be a minimum of one audio zone");
        for (CarAudioZone audioZone : carAudioZones) {
            Slog.d(CarLog.TAG_AUDIO, "CarZonesAudioFocus adding new zone " + audioZone.getId());
            CarAudioFocus zoneFocusListener = new CarAudioFocus(audioManager, packageManager);
            this.mFocusZones.put(Integer.valueOf(audioZone.getId()), zoneFocusListener);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public ArrayList<AudioFocusInfo> getAudioFocusLosersForUid(int uid, int zoneId) {
        CarAudioFocus focus = this.mFocusZones.get(Integer.valueOf(zoneId));
        return focus.getAudioFocusLosersForUid(uid);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public ArrayList<AudioFocusInfo> getAudioFocusHoldersForUid(int uid, int zoneId) {
        CarAudioFocus focus = this.mFocusZones.get(Integer.valueOf(zoneId));
        return focus.getAudioFocusHoldersForUid(uid);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void transientlyLoseInFocusInZone(ArrayList<AudioFocusInfo> afiList, int zoneId) {
        CarAudioFocus focus = this.mFocusZones.get(Integer.valueOf(zoneId));
        Iterator<AudioFocusInfo> it = afiList.iterator();
        while (it.hasNext()) {
            AudioFocusInfo info = it.next();
            focus.removeAudioFocusInfoAndTransientlyLoseFocus(info);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int reevaluateAndRegainAudioFocus(AudioFocusInfo afi) {
        CarAudioFocus focus = getFocusForAudioFocusInfo(afi);
        return focus.reevaluateAndRegainAudioFocus(afi);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setOwningPolicy(CarAudioService audioService, AudioPolicy parentPolicy) {
        this.mCarAudioService = audioService;
        this.mAudioPolicy = parentPolicy;
        for (Integer num : this.mFocusZones.keySet()) {
            int zoneId = num.intValue();
            this.mFocusZones.get(Integer.valueOf(zoneId)).setOwningPolicy(this.mCarAudioService, this.mAudioPolicy);
        }
    }

    public void onAudioFocusRequest(AudioFocusInfo afi, int requestResult) {
        CarAudioFocus focus = getFocusForAudioFocusInfo(afi);
        focus.onAudioFocusRequest(afi, requestResult);
    }

    public void onAudioFocusAbandon(AudioFocusInfo afi) {
        CarAudioFocus focus = getFocusForAudioFocusInfo(afi);
        focus.onAudioFocusAbandon(afi);
    }

    private CarAudioFocus getFocusForAudioFocusInfo(AudioFocusInfo afi) {
        int bundleZoneId;
        int zoneId = this.mCarAudioService.getZoneIdForUid(afi.getClientUid());
        Bundle bundle = afi.getAttributes().getBundle();
        if (bundle != null && (bundleZoneId = bundle.getInt("android.car.media.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID", -1)) >= 0 && bundleZoneId < this.mCarAudioService.getAudioZoneIds().length) {
            zoneId = bundleZoneId;
        }
        CarAudioFocus focus = this.mFocusZones.get(Integer.valueOf(zoneId));
        return focus;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized void dump(String indent, PrintWriter writer) {
        writer.printf("%s*CarZonesAudioFocus*\n", indent);
        writer.printf("%s\tCar Zones Audio Focus Listeners:\n", indent);
        Integer[] keys = (Integer[]) this.mFocusZones.keySet().stream().sorted().toArray(new IntFunction() { // from class: com.android.car.audio.-$$Lambda$CarZonesAudioFocus$FUzFR3OoKieFU1_dV7-Sxh-L5Ug
            @Override // java.util.function.IntFunction
            public final Object apply(int i) {
                return CarZonesAudioFocus.lambda$dump$0(i);
            }
        });
        for (Integer zoneId : keys) {
            writer.printf("%s\tZone Id: %s", indent, zoneId.toString());
            this.mFocusZones.get(zoneId).dump(indent + "\t", writer);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Integer[] lambda$dump$0(int x$0) {
        return new Integer[x$0];
    }
}
