package com.android.car;

import android.util.ArrayMap;
import com.android.internal.annotations.GuardedBy;
import java.util.ArrayList;
import java.util.Arrays;
/* loaded from: classes3.dex */
public class VmsPublishersInfo {
    private static final byte[] EMPTY_RESPONSE = new byte[0];
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final ArrayMap<InfoWrapper, Integer> mPublishersIds = new ArrayMap<>();
    @GuardedBy({"mLock"})
    private final ArrayList<InfoWrapper> mPublishersInfo = new ArrayList<>();

    /* loaded from: classes3.dex */
    private static class InfoWrapper {
        private final byte[] mInfo;

        InfoWrapper(byte[] info) {
            this.mInfo = info;
        }

        public byte[] getInfo() {
            return (byte[]) this.mInfo.clone();
        }

        public boolean equals(Object o) {
            if (!(o instanceof InfoWrapper)) {
                return false;
            }
            InfoWrapper p = (InfoWrapper) o;
            return Arrays.equals(this.mInfo, p.mInfo);
        }

        public int hashCode() {
            return Arrays.hashCode(this.mInfo);
        }
    }

    public int getIdForInfo(byte[] publisherInfo) {
        Integer publisherId;
        InfoWrapper wrappedPublisherInfo = new InfoWrapper(publisherInfo);
        synchronized (this.mLock) {
            publisherId = this.mPublishersIds.get(wrappedPublisherInfo);
            if (publisherId == null) {
                this.mPublishersInfo.add(wrappedPublisherInfo);
                publisherId = Integer.valueOf(this.mPublishersInfo.size());
                this.mPublishersIds.put(wrappedPublisherInfo, publisherId);
            }
        }
        return publisherId.intValue();
    }

    public byte[] getPublisherInfo(int publisherId) {
        byte[] info;
        synchronized (this.mLock) {
            if (publisherId >= 1) {
                if (publisherId <= this.mPublishersInfo.size()) {
                    info = this.mPublishersInfo.get(publisherId - 1).getInfo();
                }
            }
            info = EMPTY_RESPONSE;
        }
        return info;
    }
}
