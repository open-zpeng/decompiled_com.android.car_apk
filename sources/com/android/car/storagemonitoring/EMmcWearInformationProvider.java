package com.android.car.storagemonitoring;

import android.util.Slog;
import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
/* loaded from: classes3.dex */
public class EMmcWearInformationProvider implements WearInformationProvider {
    private static File DEFAULT_LIFE_TIME_FILE = new File("/sys/bus/mmc/devices/mmc0:0001/life_time");
    private static File DEFAULT_PRE_EOL_FILE = new File("/sys/bus/mmc/devices/mmc0:0001/pre_eol_info");
    private File mLifetimeFile;
    private File mPreEolFile;

    public EMmcWearInformationProvider() {
        this(DEFAULT_LIFE_TIME_FILE, DEFAULT_PRE_EOL_FILE);
    }

    @VisibleForTesting
    EMmcWearInformationProvider(File lifetimeFile, File preEolFile) {
        this.mLifetimeFile = lifetimeFile;
        this.mPreEolFile = preEolFile;
    }

    private String readLineFromFile(File f) {
        if (!f.exists() || !f.isFile()) {
            Slog.i(CarLog.TAG_STORAGE, f + " does not exist or is not a file");
            return null;
        }
        BufferedReader reader = null;
        try {
            try {
                reader = new BufferedReader(new FileReader(f));
                String data = reader.readLine();
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return data;
            } catch (IOException e2) {
                Slog.w(CarLog.TAG_STORAGE, f + " cannot be read from", e2);
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                return null;
            }
        } catch (Throwable th) {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
            }
            throw th;
        }
    }

    @Override // com.android.car.storagemonitoring.WearInformationProvider
    public WearInformation load() {
        String lifetimeData = readLineFromFile(this.mLifetimeFile);
        String eolData = readLineFromFile(this.mPreEolFile);
        if (lifetimeData == null || eolData == null) {
            return null;
        }
        String[] lifetimes = lifetimeData.split(" ");
        if (lifetimes.length != 2) {
            Slog.w(CarLog.TAG_STORAGE, "lifetime data not in expected format: " + lifetimeData);
            return null;
        }
        try {
            int lifetimeA = Integer.decode(lifetimes[0]).intValue();
            int lifetimeB = Integer.decode(lifetimes[1]).intValue();
            int eol = Integer.decode("0x" + eolData).intValue();
            return new WearInformation(convertLifetime(lifetimeA), convertLifetime(lifetimeB), adjustEol(eol));
        } catch (NumberFormatException e) {
            Slog.w(CarLog.TAG_STORAGE, "lifetime data not in expected format: " + lifetimeData, e);
            return null;
        }
    }
}
