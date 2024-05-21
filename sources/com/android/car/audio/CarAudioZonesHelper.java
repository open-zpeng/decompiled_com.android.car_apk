package com.android.car.audio;

import android.content.Context;
import android.util.SparseArray;
import android.util.Xml;
import android.view.DisplayAddress;
import androidx.core.app.NotificationCompat;
import com.android.internal.util.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
class CarAudioZonesHelper {
    private static final String ATTR_CONTEXT_NAME = "context";
    private static final String ATTR_DEVICE_ADDRESS = "address";
    private static final String ATTR_IS_PRIMARY = "isPrimary";
    private static final String ATTR_PHYSICAL_PORT = "port";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_ZONE_NAME = "name";
    private static final int SUPPORTED_VERSION = 1;
    private static final String TAG_AUDIO_DEVICE = "device";
    private static final String TAG_AUDIO_ZONE = "zone";
    private static final String TAG_AUDIO_ZONES = "zones";
    private static final String TAG_CONTEXT = "context";
    private static final String TAG_DISPLAY = "display";
    private static final String TAG_DISPLAYS = "displays";
    private static final String TAG_ROOT = "carAudioConfiguration";
    private static final String TAG_VOLUME_GROUP = "group";
    private static final String TAG_VOLUME_GROUPS = "volumeGroups";
    private final SparseArray<CarAudioDeviceInfo> mBusToCarAudioDeviceInfo;
    private final Context mContext;
    private boolean mHasPrimaryZone;
    private final InputStream mInputStream;
    private int mNextSecondaryZoneId = 1;
    private final Set<Long> mPortIds = new HashSet();
    private static final String NAMESPACE = null;
    private static final Map<String, Integer> CONTEXT_NAME_MAP = new HashMap();

    static {
        CONTEXT_NAME_MAP.put("music", 1);
        CONTEXT_NAME_MAP.put(NotificationCompat.CATEGORY_NAVIGATION, 2);
        CONTEXT_NAME_MAP.put("voice_command", 3);
        CONTEXT_NAME_MAP.put("call_ring", 4);
        CONTEXT_NAME_MAP.put(NotificationCompat.CATEGORY_CALL, 5);
        CONTEXT_NAME_MAP.put(NotificationCompat.CATEGORY_ALARM, 6);
        CONTEXT_NAME_MAP.put("notification", 7);
        CONTEXT_NAME_MAP.put("system_sound", 8);
        CONTEXT_NAME_MAP.put("avas", 9);
        CONTEXT_NAME_MAP.put("massage_seat", 10);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioZonesHelper(Context context, InputStream inputStream, SparseArray<CarAudioDeviceInfo> busToCarAudioDeviceInfo) {
        this.mContext = context;
        this.mInputStream = inputStream;
        this.mBusToCarAudioDeviceInfo = busToCarAudioDeviceInfo;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioZone[] loadAudioZones() throws IOException, XmlPullParserException {
        List<CarAudioZone> carAudioZones = new ArrayList<>();
        parseCarAudioZones(carAudioZones, this.mInputStream);
        return (CarAudioZone[]) carAudioZones.toArray(new CarAudioZone[0]);
    }

    private void parseCarAudioZones(List<CarAudioZone> carAudioZones, InputStream stream) throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", NAMESPACE != null);
        parser.setInput(stream, null);
        parser.nextTag();
        parser.require(2, NAMESPACE, TAG_ROOT);
        int versionNumber = Integer.parseInt(parser.getAttributeValue(NAMESPACE, ATTR_VERSION));
        if (versionNumber != 1) {
            throw new RuntimeException("Support version:1 only, got version:" + versionNumber);
        }
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (TAG_AUDIO_ZONES.equals(parser.getName())) {
                    parseAudioZones(parser, carAudioZones);
                } else {
                    skip(parser);
                }
            }
        }
    }

    private void parseAudioZones(XmlPullParser parser, List<CarAudioZone> carAudioZones) throws XmlPullParserException, IOException {
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (TAG_AUDIO_ZONE.equals(parser.getName())) {
                    carAudioZones.add(parseAudioZone(parser));
                } else {
                    skip(parser);
                }
            }
        }
        Preconditions.checkArgument(this.mHasPrimaryZone, "Requires one primary zone");
        carAudioZones.sort(Comparator.comparing(new Function() { // from class: com.android.car.audio.-$$Lambda$7364NHkWOQanKBYtea7U_Ri_ci4
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return Integer.valueOf(((CarAudioZone) obj).getId());
            }
        }));
    }

    private CarAudioZone parseAudioZone(XmlPullParser parser) throws XmlPullParserException, IOException {
        boolean isPrimary = Boolean.parseBoolean(parser.getAttributeValue(NAMESPACE, ATTR_IS_PRIMARY));
        if (isPrimary) {
            Preconditions.checkArgument(!this.mHasPrimaryZone, "Only one primary zone is allowed");
            this.mHasPrimaryZone = true;
        }
        String zoneName = parser.getAttributeValue(NAMESPACE, "name");
        CarAudioZone zone = new CarAudioZone(isPrimary ? 0 : getNextSecondaryZoneId(), zoneName);
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (TAG_VOLUME_GROUPS.equals(parser.getName())) {
                    parseVolumeGroups(parser, zone);
                } else if (TAG_DISPLAYS.equals(parser.getName())) {
                    parseDisplays(parser, zone);
                } else {
                    skip(parser);
                }
            }
        }
        return zone;
    }

    private void parseDisplays(XmlPullParser parser, CarAudioZone zone) throws IOException, XmlPullParserException {
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (TAG_DISPLAY.equals(parser.getName())) {
                    zone.addPhysicalDisplayAddress(parsePhysicalDisplayAddress(parser));
                }
                skip(parser);
            }
        }
    }

    private DisplayAddress.Physical parsePhysicalDisplayAddress(XmlPullParser parser) {
        String port = parser.getAttributeValue(NAMESPACE, ATTR_PHYSICAL_PORT);
        try {
            long portId = Long.parseLong(port);
            validatePortIsUnique(Long.valueOf(portId));
            return DisplayAddress.fromPhysicalDisplayId(portId);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Port " + port + " is not a number", e);
        }
    }

    private void validatePortIsUnique(Long portId) {
        if (this.mPortIds.contains(portId)) {
            throw new RuntimeException("Port Id " + portId + " is already associated with a zone");
        }
        this.mPortIds.add(portId);
    }

    private void parseVolumeGroups(XmlPullParser parser, CarAudioZone zone) throws XmlPullParserException, IOException {
        int groupId = 0;
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (TAG_VOLUME_GROUP.equals(parser.getName())) {
                    zone.addVolumeGroup(parseVolumeGroup(parser, zone.getId(), groupId));
                    groupId++;
                } else {
                    skip(parser);
                }
            }
        }
    }

    private CarVolumeGroup parseVolumeGroup(XmlPullParser parser, int zoneId, int groupId) throws XmlPullParserException, IOException {
        CarVolumeGroup group = new CarVolumeGroup(this.mContext, zoneId, groupId);
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if (TAG_AUDIO_DEVICE.equals(parser.getName())) {
                    String address = parser.getAttributeValue(NAMESPACE, ATTR_DEVICE_ADDRESS);
                    parseVolumeGroupContexts(parser, group, CarAudioDeviceInfo.parseDeviceAddress(address));
                } else {
                    skip(parser);
                }
            }
        }
        return group;
    }

    private void parseVolumeGroupContexts(XmlPullParser parser, CarVolumeGroup group, int busNumber) throws XmlPullParserException, IOException {
        while (parser.next() != 3) {
            if (parser.getEventType() == 2) {
                if ("context".equals(parser.getName())) {
                    group.bind(parseContextNumber(parser.getAttributeValue(NAMESPACE, "context")), busNumber, this.mBusToCarAudioDeviceInfo.get(busNumber));
                }
                skip(parser);
            }
        }
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != 2) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            int next = parser.next();
            if (next == 2) {
                depth++;
            } else if (next == 3) {
                depth--;
            }
        }
    }

    private int parseContextNumber(String context) {
        return CONTEXT_NAME_MAP.getOrDefault(context.toLowerCase(), 0).intValue();
    }

    private int getNextSecondaryZoneId() {
        int zoneId = this.mNextSecondaryZoneId;
        this.mNextSecondaryZoneId++;
        return zoneId;
    }
}
