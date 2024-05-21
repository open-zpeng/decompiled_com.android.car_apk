package com.android.car;

import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.android.car.audio.CarAudioService;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
/* loaded from: classes3.dex */
public class SystemStateControllerService implements CarServiceBase {
    private static final int MAINTENANCE_SERVICE_TURNOFF_PERIOD = 100;
    private static final int MSG_NOTIFY_PROCESSINGCOMPLETE_EARLY = 0;
    private static String TAG = "SystemStateControllerService";
    private final CarAudioService mCarAudioService;
    private CarPowerManager mCarPowerManager;
    private Context mContext;
    private CompletableFuture<Void> mFuture;
    private final ICarImpl mICarImpl;
    private final boolean mLockWhenMuting;
    private final Object mLock = new Object();
    private SystemStateHandler mSystemStateHandler = null;
    private final CarPowerManager.CarPowerStateListenerWithCompletion mCarPowerStateListener = new CarPowerManager.CarPowerStateListenerWithCompletion() { // from class: com.android.car.SystemStateControllerService.1
        public void onStateChanged(int state, CompletableFuture<Void> future) {
            Log.d(CarLog.TAG_AUDIO, SystemStateControllerService.TAG + " onStateChanged State : " + state);
            if (state == 7) {
                Log.d(CarLog.TAG_AUDIO, SystemStateControllerService.TAG + " SHUTDOWN_PREPARE ");
                SystemStateControllerService.this.handlePowerOff(future);
            } else if (future != null) {
                future.complete(null);
            }
        }
    };

    public SystemStateControllerService(Context context, CarAudioService carAudioService, ICarImpl carImpl) {
        this.mContext = context;
        this.mCarAudioService = carAudioService;
        this.mICarImpl = carImpl;
        Resources res = context.getResources();
        this.mLockWhenMuting = res.getBoolean(R.bool.displayOffMuteLockAllAudio);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handlePowerOff(CompletableFuture<Void> future) {
        setFuture(future);
        this.mSystemStateHandler.sendEmptyMessage(0);
    }

    private void setFuture(CompletableFuture<Void> future) {
        synchronized (this.mLock) {
            this.mFuture = future;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void completeFuture() {
        synchronized (this.mLock) {
            if (this.mFuture != null) {
                this.mFuture.complete(null);
                this.mFuture = null;
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.setListenerWithCompletion(this.mCarPowerStateListener);
            this.mSystemStateHandler = new SystemStateHandler();
            return;
        }
        Log.e(CarLog.TAG_AUDIO, "Failed to get car power manager");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class SystemStateHandler extends Handler {
        private SystemStateHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                if (!SystemStateControllerService.this.mCarAudioService.getAudioStatus()) {
                    SystemStateControllerService.this.mSystemStateHandler.removeMessages(0);
                    SystemStateControllerService.this.completeFuture();
                    Log.d(CarLog.TAG_AUDIO, SystemStateControllerService.TAG + " music is not active so calling completeFuture");
                    return;
                }
                SystemStateControllerService.this.mSystemStateHandler.removeMessages(0);
                SystemStateControllerService.this.mSystemStateHandler.sendEmptyMessageDelayed(0, 100L);
                Log.d(CarLog.TAG_AUDIO, SystemStateControllerService.TAG + " music is active");
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.clearListener();
            this.mCarPowerManager = null;
        }
        if (this.mSystemStateHandler != null) {
            this.mSystemStateHandler = null;
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
    }
}
