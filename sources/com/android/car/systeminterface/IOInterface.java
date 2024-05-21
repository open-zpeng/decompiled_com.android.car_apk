package com.android.car.systeminterface;

import android.content.Context;
import java.io.File;
/* loaded from: classes3.dex */
public interface IOInterface {
    File getSystemCarDir();

    /* loaded from: classes3.dex */
    public static class DefaultImpl implements IOInterface {
        private final File mSystemCarDir = new File("/data/system/car");

        /* JADX INFO: Access modifiers changed from: package-private */
        public DefaultImpl(Context context) {
        }

        @Override // com.android.car.systeminterface.IOInterface
        public File getSystemCarDir() {
            return this.mSystemCarDir;
        }
    }
}
