package com.android.car.audio;

import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.hardware.automotive.audiocontrol.V1_0.IAudioControl;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.Xml;
import com.android.car.CarLog;
import com.android.car.R;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import org.xmlpull.v1.XmlPullParserException;
/* JADX INFO: Access modifiers changed from: package-private */
@Deprecated
/* loaded from: classes3.dex */
public class CarAudioZonesHelperLegacy {
    private static final String TAG_CONTEXT = "context";
    private static final String TAG_GROUP = "group";
    private static final String TAG_VOLUME_GROUPS = "volumeGroups";
    private final SparseArray<CarAudioDeviceInfo> mBusToCarAudioDeviceInfo;
    private final Context mContext;
    private final SparseIntArray mContextToBus = new SparseIntArray();
    private final int mXmlConfiguration;

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioZonesHelperLegacy(Context context, int xmlConfiguration, SparseArray<CarAudioDeviceInfo> busToCarAudioDeviceInfo, IAudioControl audioControl) {
        int[] iArr;
        this.mContext = context;
        this.mXmlConfiguration = xmlConfiguration;
        this.mBusToCarAudioDeviceInfo = busToCarAudioDeviceInfo;
        try {
            for (int contextNumber : CarAudioDynamicRouting.CONTEXT_NUMBERS) {
                this.mContextToBus.put(contextNumber, audioControl.getBusForContext(contextNumber));
            }
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_AUDIO, "Failed to query IAudioControl HAL", e);
            e.rethrowAsRuntimeException();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioZone[] loadAudioZones() {
        int[] contexts;
        CarAudioZone zone = new CarAudioZone(0, "Primary zone");
        for (CarVolumeGroup group : loadVolumeGroups()) {
            zone.addVolumeGroup(group);
            for (int contextNumber : group.getContexts()) {
                int busNumber = this.mContextToBus.get(contextNumber);
                group.bind(contextNumber, busNumber, this.mBusToCarAudioDeviceInfo.get(busNumber));
            }
        }
        return new CarAudioZone[]{zone};
    }

    private List<CarVolumeGroup> loadVolumeGroups() {
        XmlResourceParser parser;
        AttributeSet attrs;
        List<CarVolumeGroup> carVolumeGroups = new ArrayList<>();
        try {
            parser = this.mContext.getResources().getXml(this.mXmlConfiguration);
            attrs = Xml.asAttributeSet(parser);
            while (true) {
                int type = parser.next();
                if (type == 1 || type == 2) {
                    break;
                }
            }
        } catch (Exception e) {
            Slog.e(CarLog.TAG_AUDIO, "Error parsing volume groups configuration", e);
        }
        if (!TAG_VOLUME_GROUPS.equals(parser.getName())) {
            throw new RuntimeException("Meta-data does not start with volumeGroups tag");
        }
        int outerDepth = parser.getDepth();
        int id = 0;
        while (true) {
            int type2 = parser.next();
            if (type2 == 1 || (type2 == 3 && parser.getDepth() <= outerDepth)) {
                break;
            } else if (type2 != 3 && TAG_GROUP.equals(parser.getName())) {
                carVolumeGroups.add(parseVolumeGroup(id, attrs, parser));
                id++;
            }
        }
        parser.close();
        return carVolumeGroups;
    }

    private CarVolumeGroup parseVolumeGroup(int id, AttributeSet attrs, XmlResourceParser parser) throws XmlPullParserException, IOException {
        List<Integer> contexts = new ArrayList<>();
        int innerDepth = parser.getDepth();
        while (true) {
            int type = parser.next();
            if (type == 1 || (type == 3 && parser.getDepth() <= innerDepth)) {
                break;
            } else if (type != 3 && TAG_CONTEXT.equals(parser.getName())) {
                TypedArray c = this.mContext.getResources().obtainAttributes(attrs, R.styleable.volumeGroups_context);
                contexts.add(Integer.valueOf(c.getInt(0, -1)));
                c.recycle();
            }
        }
        return new CarVolumeGroup(this.mContext, 0, id, contexts.stream().mapToInt(new ToIntFunction() { // from class: com.android.car.audio.-$$Lambda$CarAudioZonesHelperLegacy$a_qUQniZEZrnCYadb8JWi46Doe8
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int intValue;
                intValue = ((Integer) obj).intValue();
                return intValue;
            }
        }).filter(new IntPredicate() { // from class: com.android.car.audio.-$$Lambda$CarAudioZonesHelperLegacy$CHnHz6w92bXafaLHQzv5cqhNmbM
            @Override // java.util.function.IntPredicate
            public final boolean test(int i) {
                return CarAudioZonesHelperLegacy.lambda$parseVolumeGroup$1(i);
            }
        }).toArray());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$parseVolumeGroup$1(int i) {
        return i >= 0;
    }
}
