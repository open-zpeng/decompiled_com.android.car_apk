package com.android.car;

import android.car.ICarBugreportCallback;
import android.car.ICarBugreportService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
/* loaded from: classes3.dex */
public class CarBugreportManagerService extends ICarBugreportService.Stub implements CarServiceBase {
    private static final String BEGIN_PREFIX = "BEGIN:";
    private static final String BUGREPORTD_SERVICE = "car-bugreportd";
    private static final String BUGREPORT_EXTRA_OUTPUT_SOCKET = "car_br_extra_output_socket";
    private static final String BUGREPORT_OUTPUT_SOCKET = "car_br_output_socket";
    private static final String BUGREPORT_PROGRESS_SOCKET = "car_br_progress_socket";
    private static final String FAIL_PREFIX = "FAIL:";
    private static final String OK_PREFIX = "OK:";
    private static final String PROGRESS_PREFIX = "PROGRESS:";
    private static final int SOCKET_CONNECTION_MAX_RETRY = 10;
    private static final int SOCKET_CONNECTION_RETRY_DELAY_IN_MS = 5000;
    private static final String TAG = "CarBugreportMgrService";
    private final Context mContext;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private boolean mIsServiceRunning;
    private final Object mLock = new Object();

    public CarBugreportManagerService(Context context) {
        this.mContext = context;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        this.mHandlerThread = new HandlerThread(TAG);
        this.mHandlerThread.start();
        this.mHandler = new Handler(this.mHandlerThread.getLooper());
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        this.mHandlerThread.quitSafely();
    }

    public void requestBugreport(ParcelFileDescriptor output, ParcelFileDescriptor extraOutput, ICarBugreportCallback callback) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DUMP", "requestZippedBugreport");
        PackageManager pm = this.mContext.getPackageManager();
        int callingUid = Binder.getCallingUid();
        if (pm.checkSignatures(Process.myUid(), callingUid) != 0) {
            throw new SecurityException("Caller " + pm.getNameForUid(callingUid) + " does not have the right signature");
        }
        String defaultAppPkgName = this.mContext.getString(R.string.config_car_bugreport_application);
        String[] packageNamesForCallerUid = pm.getPackagesForUid(callingUid);
        boolean found = false;
        if (packageNamesForCallerUid != null) {
            int length = packageNamesForCallerUid.length;
            int i = 0;
            while (true) {
                if (i >= length) {
                    break;
                }
                String packageName = packageNamesForCallerUid[i];
                if (!defaultAppPkgName.equals(packageName)) {
                    i++;
                } else {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new SecurityException("Caller " + pm.getNameForUid(callingUid) + " is not a designated bugreport app");
        }
        synchronized (this.mLock) {
            requestBugReportLocked(output, extraOutput, callback);
        }
    }

    @GuardedBy({"mLock"})
    private void requestBugReportLocked(final ParcelFileDescriptor output, final ParcelFileDescriptor extraOutput, final ICarBugreportCallback callback) {
        if (this.mIsServiceRunning) {
            Slog.w(TAG, "Bugreport Service already running");
            reportError(callback, 2);
            return;
        }
        this.mIsServiceRunning = true;
        this.mHandler.post(new Runnable() { // from class: com.android.car.-$$Lambda$CarBugreportManagerService$AG6L1uBySyDSdAjmnFy5_-H9Ci0
            @Override // java.lang.Runnable
            public final void run() {
                CarBugreportManagerService.this.lambda$requestBugReportLocked$0$CarBugreportManagerService(output, extraOutput, callback);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: startBugreportd */
    public void lambda$requestBugReportLocked$0$CarBugreportManagerService(ParcelFileDescriptor output, ParcelFileDescriptor extraOutput, ICarBugreportCallback callback) {
        Slog.i(TAG, "Starting car-bugreportd");
        try {
            SystemProperties.set("ctl.start", BUGREPORTD_SERVICE);
            processBugreportSockets(output, extraOutput, callback);
            synchronized (this.mLock) {
                this.mIsServiceRunning = false;
            }
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to start car-bugreportd", e);
            reportError(callback, 1);
        }
    }

    private void handleProgress(String line, ICarBugreportCallback callback) {
        String progressOverTotal = line.substring(PROGRESS_PREFIX.length());
        String[] parts = progressOverTotal.split("/");
        if (parts.length != 2) {
            Slog.w(TAG, "Invalid progress line from bugreportz: " + line);
            return;
        }
        try {
            float progress = Float.parseFloat(parts[0]);
            float total = Float.parseFloat(parts[1]);
            if (total == 0.0f) {
                Slog.w(TAG, "Invalid progress total value: " + line);
                return;
            }
            try {
                callback.onProgress((100.0f * progress) / total);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to call onProgress callback", e);
            }
        } catch (NumberFormatException e2) {
            Slog.w(TAG, "Invalid progress value: " + line, e2);
        }
    }

    private void handleFinished(ParcelFileDescriptor output, ParcelFileDescriptor extraOutput, ICarBugreportCallback callback) {
        Slog.i(TAG, "Finished reading bugreport");
        if (!copySocketToPfd(output, BUGREPORT_OUTPUT_SOCKET, callback) || !copySocketToPfd(extraOutput, BUGREPORT_EXTRA_OUTPUT_SOCKET, callback)) {
            return;
        }
        try {
            callback.onFinished();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to call onFinished callback", e);
        }
    }

    private void processBugreportSockets(ParcelFileDescriptor output, ParcelFileDescriptor extraOutput, ICarBugreportCallback callback) {
        LocalSocket localSocket = connectSocket(BUGREPORT_PROGRESS_SOCKET);
        if (localSocket == null) {
            reportError(callback, 3);
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(localSocket.getInputStream()));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    Slog.e(TAG, "dumpstate progress unexpectedly ended");
                    reportError(callback, 1);
                    $closeResource(null, reader);
                    return;
                } else if (line.startsWith(PROGRESS_PREFIX)) {
                    handleProgress(line, callback);
                } else if (line.startsWith(FAIL_PREFIX)) {
                    String errorMessage = line.substring(FAIL_PREFIX.length());
                    Slog.e(TAG, "Failed to dumpstate: " + errorMessage);
                    reportError(callback, 1);
                    $closeResource(null, reader);
                    return;
                } else if (line.startsWith(OK_PREFIX)) {
                    handleFinished(output, extraOutput, callback);
                    $closeResource(null, reader);
                    return;
                } else if (!line.startsWith(BEGIN_PREFIX)) {
                    Slog.w(TAG, "Received unknown progress line from dumpstate: " + line);
                }
            }
        } catch (IOException | RuntimeException e) {
            Slog.i(TAG, "Failed to read from progress socket", e);
            reportError(callback, 3);
        }
    }

    private static /* synthetic */ void $closeResource(Throwable x0, AutoCloseable x1) {
        if (x0 == null) {
            x1.close();
            return;
        }
        try {
            x1.close();
        } catch (Throwable th) {
            x0.addSuppressed(th);
        }
    }

    private boolean copySocketToPfd(ParcelFileDescriptor pfd, String remoteSocket, ICarBugreportCallback callback) {
        LocalSocket localSocket = connectSocket(remoteSocket);
        if (localSocket == null) {
            reportError(callback, 3);
            return false;
        }
        try {
            DataInputStream in = new DataInputStream(localSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pfd));
            rawCopyStream(out, in);
            $closeResource(null, out);
            $closeResource(null, in);
            return true;
        } catch (IOException | RuntimeException e) {
            Slog.e(TAG, "Failed to grab dump state from car_br_output_socket", e);
            reportError(callback, 1);
            return false;
        }
    }

    private void reportError(ICarBugreportCallback callback, int errorCode) {
        try {
            callback.onError(errorCode);
        } catch (RemoteException e) {
            Slog.e(TAG, "onError() failed: " + e.getMessage());
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
    }

    private LocalSocket connectSocket(String socketName) {
        LocalSocket socket = new LocalSocket();
        int retryCount = 0;
        while (true) {
            SystemClock.sleep(5000L);
            try {
                socket.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.RESERVED));
                return socket;
            } catch (IOException e) {
                retryCount++;
                if (retryCount >= 10) {
                    Slog.i(TAG, "Failed to connect to dumpstate socket " + socketName + " after " + retryCount + " retries", e);
                    return null;
                }
                Slog.i(TAG, "Failed to connect to " + socketName + ". Will try again " + e.getMessage());
            }
        }
    }

    private static void rawCopyStream(OutputStream writer, InputStream reader) throws IOException {
        byte[] buf = new byte[8192];
        while (true) {
            int read = reader.read(buf, 0, buf.length);
            if (read > 0) {
                writer.write(buf, 0, read);
            } else {
                return;
            }
        }
    }
}
