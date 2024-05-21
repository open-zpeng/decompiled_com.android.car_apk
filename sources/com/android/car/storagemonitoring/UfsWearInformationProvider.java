package com.android.car.storagemonitoring;

import android.util.Slog;
import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
/* loaded from: classes3.dex */
public class UfsWearInformationProvider implements WearInformationProvider {
    private static File DEFAULT_FILE = new File("/sys/devices/soc/624000.ufshc/health");
    private File mFile;

    public UfsWearInformationProvider() {
        this(DEFAULT_FILE);
    }

    @VisibleForTesting
    public UfsWearInformationProvider(File file) {
        this.mFile = file;
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // com.android.car.storagemonitoring.WearInformationProvider
    public WearInformation load() {
        char c;
        try {
            List<String> lifetimeData = Files.readAllLines(this.mFile.toPath());
            if (lifetimeData == null || lifetimeData.size() < 4) {
                return null;
            }
            Pattern infoPattern = Pattern.compile("Health Descriptor\\[Byte offset 0x\\d+\\]: (\\w+) = 0x([0-9a-fA-F]+)");
            Optional<Integer> lifetimeA = Optional.empty();
            Optional<Integer> lifetimeB = Optional.empty();
            Optional<Integer> eol = Optional.empty();
            Optional<Integer> eol2 = eol;
            Optional<Integer> lifetimeB2 = lifetimeB;
            Optional<Integer> lifetimeA2 = lifetimeA;
            for (String lifetimeInfo : lifetimeData) {
                Scanner scanner = new Scanner(lifetimeInfo);
                if (scanner.findInLine(infoPattern) != null) {
                    MatchResult match = scanner.match();
                    if (match.groupCount() == 2) {
                        String name = match.group(1);
                        String value = "0x" + match.group(2);
                        try {
                            try {
                                switch (name.hashCode()) {
                                    case -1031905156:
                                        if (name.equals("bDeviceLifeTimeEstA")) {
                                            c = 1;
                                            break;
                                        }
                                        c = 65535;
                                        break;
                                    case -1031905155:
                                        if (name.equals("bDeviceLifeTimeEstB")) {
                                            c = 2;
                                            break;
                                        }
                                        c = 65535;
                                        break;
                                    case 637439663:
                                        if (name.equals("bPreEOLInfo")) {
                                            c = 0;
                                            break;
                                        }
                                        c = 65535;
                                        break;
                                    default:
                                        c = 65535;
                                        break;
                                }
                                if (c != 0) {
                                    if (c == 1) {
                                        lifetimeA2 = Optional.of(Integer.decode(value));
                                    } else if (c == 2) {
                                        lifetimeB2 = Optional.of(Integer.decode(value));
                                    }
                                } else {
                                    eol2 = Optional.of(Integer.decode(value));
                                }
                            } catch (NumberFormatException e) {
                                Slog.w(CarLog.TAG_STORAGE, "trying to decode key " + name + " value " + value + " didn't parse properly", e);
                            }
                        } finally {
                            scanner.close();
                        }
                    }
                }
            }
            if (!lifetimeA2.isPresent() || !lifetimeB2.isPresent() || !eol2.isPresent()) {
                return null;
            }
            return new WearInformation(convertLifetime(lifetimeA2.get().intValue()), convertLifetime(lifetimeB2.get().intValue()), adjustEol(eol2.get().intValue()));
        } catch (IOException e2) {
            Slog.w(CarLog.TAG_STORAGE, "error reading " + this.mFile, e2);
            return null;
        }
    }
}
