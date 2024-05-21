package com.android.car.protobuf;
/* loaded from: classes3.dex */
interface MutabilityOracle {
    public static final MutabilityOracle IMMUTABLE = new MutabilityOracle() { // from class: com.android.car.protobuf.MutabilityOracle.1
        @Override // com.android.car.protobuf.MutabilityOracle
        public void ensureMutable() {
            throw new UnsupportedOperationException();
        }
    };

    void ensureMutable();
}
