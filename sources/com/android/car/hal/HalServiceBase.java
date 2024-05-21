package com.android.car.hal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
/* loaded from: classes3.dex */
public abstract class HalServiceBase {
    static final int NOT_SUPPORTED_PROPERTY = -1;
    private final LinkedList<VehiclePropValue> mDispatchList = new LinkedList<>();

    public abstract void dump(PrintWriter printWriter);

    public abstract void handleHalEvents(List<VehiclePropValue> list);

    public abstract void init();

    public abstract void release();

    public List<VehiclePropValue> getDispatchList() {
        return this.mDispatchList;
    }

    public Collection<VehiclePropConfig> takeSupportedProperties(Collection<VehiclePropConfig> allProperties) {
        return null;
    }

    public void handlePropertySetError(int property, int errorCode) {
    }

    /* loaded from: classes3.dex */
    static class ManagerToHalPropIdMap {
        private final BidirectionalSparseIntArray mMap;

        static ManagerToHalPropIdMap create(int... mgrToHalPropIds) {
            return new ManagerToHalPropIdMap(BidirectionalSparseIntArray.create(mgrToHalPropIds));
        }

        private ManagerToHalPropIdMap(BidirectionalSparseIntArray map) {
            this.mMap = map;
        }

        int getHalPropId(int managerPropId) {
            return this.mMap.getValue(managerPropId, -1);
        }

        int getManagerPropId(int halPropId) {
            return this.mMap.getKey(halPropId, -1);
        }
    }
}
