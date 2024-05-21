package com.android.car.vehiclehal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.util.JsonReader;
import java.io.IOException;
/* loaded from: classes3.dex */
public class DiagnosticJsonReader {
    public static final String FRAME_TYPE_FREEZE = "freeze";
    public static final String FRAME_TYPE_LIVE = "live";
    private final DiagnosticEventBuilder mFreezeFrameBuilder;
    private final DiagnosticEventBuilder mLiveFrameBuilder;

    public DiagnosticJsonReader(VehiclePropConfig liveConfig, VehiclePropConfig freezeConfig) {
        this.mLiveFrameBuilder = new DiagnosticEventBuilder(VehicleProperty.OBD2_LIVE_FRAME, liveConfig.configArray.get(0).intValue(), liveConfig.configArray.get(1).intValue());
        this.mFreezeFrameBuilder = new DiagnosticEventBuilder(VehicleProperty.OBD2_FREEZE_FRAME, freezeConfig.configArray.get(0).intValue(), freezeConfig.configArray.get(1).intValue());
    }

    public DiagnosticJsonReader() {
        this.mLiveFrameBuilder = new DiagnosticEventBuilder((int) VehicleProperty.OBD2_LIVE_FRAME);
        this.mFreezeFrameBuilder = new DiagnosticEventBuilder((int) VehicleProperty.OBD2_FREEZE_FRAME);
    }

    public VehiclePropValue build(JsonReader jsonReader) throws IOException {
        char c;
        DiagnosticJson diagnosticJson = DiagnosticJson.build(jsonReader);
        String str = diagnosticJson.type;
        int hashCode = str.hashCode();
        if (hashCode != -1266402665) {
            if (hashCode == 3322092 && str.equals(FRAME_TYPE_LIVE)) {
                c = 0;
            }
            c = 65535;
        } else {
            if (str.equals(FRAME_TYPE_FREEZE)) {
                c = 1;
            }
            c = 65535;
        }
        if (c != 0) {
            if (c == 1) {
                return diagnosticJson.build(this.mFreezeFrameBuilder);
            }
            return null;
        }
        return diagnosticJson.build(this.mLiveFrameBuilder);
    }
}
