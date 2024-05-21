package com.android.car.trust;

import com.android.car.BLEStreamProtos.BLEMessageProto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
/* loaded from: classes3.dex */
class BLEMessagePayloadStream {
    private boolean mIsComplete;
    private ByteArrayOutputStream mPendingData = new ByteArrayOutputStream();

    public void reset() {
        this.mPendingData.reset();
        this.mIsComplete = false;
    }

    public void write(BLEMessageProto.BLEMessage message) throws IOException {
        this.mPendingData.write(message.getPayload().toByteArray());
        this.mIsComplete = message.getPacketNumber() == message.getTotalPackets();
    }

    public boolean isComplete() {
        return this.mIsComplete;
    }

    public byte[] toByteArray() {
        return this.mPendingData.toByteArray();
    }
}
