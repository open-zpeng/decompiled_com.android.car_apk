package com.android.car;

import android.bluetooth.BluetoothAdapter;
import android.car.hardware.power.CarPowerManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
/* loaded from: classes3.dex */
public class AirplaneModeService implements CarServiceBase, CarPowerManager.CarPowerStateListenerWithCompletion {
    private static final boolean DBG = true;
    private static final String TAG = "AirplaneModeService";
    private final Context mContext;
    private boolean mEnablePowerManager;
    private CompletableFuture<Void> mFuture;
    private CarPowerManagementService mCarPowerManagementService = null;
    private CarPowerManager mCarPowerManager = null;
    private HandlerThread mThread = null;
    private AirplaneModeHandler mAirplaneModeHandler = null;
    private final Object mLock = new Object();

    public AirplaneModeService(Context context) {
        this.mEnablePowerManager = false;
        this.mContext = context;
        this.mEnablePowerManager = this.mContext.getResources().getBoolean(R.bool.enableAirplaneModeService);
        logd("mEnablePowerManager: " + this.mEnablePowerManager);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        logd("init ");
        if (this.mEnablePowerManager) {
            this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
            if (this.mCarPowerManager != null) {
                logd("Listen car power state ");
                this.mCarPowerManager.setListenerWithCompletion(this);
                this.mThread = new HandlerThread(TAG);
                this.mThread.start();
                this.mAirplaneModeHandler = new AirplaneModeHandler(this.mThread.getLooper());
                this.mAirplaneModeHandler.setAirplaneModeOn(false);
                return;
            }
            loge("Can't get CarPowerManager");
        }
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
        logd("release ");
        if (this.mCarPowerManager != null) {
            this.mCarPowerManager.clearListener();
            this.mCarPowerManager = null;
        }
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void dump(PrintWriter writer) {
        writer.println("**dump AirplaneModeService**");
        if (this.mAirplaneModeHandler != null) {
            this.mAirplaneModeHandler.dump(writer);
        }
    }

    public void onStateChanged(int state, CompletableFuture<Void> future) {
        logd("onStateChanged: " + state);
        if (state == 2) {
            handleSuspendEnter();
            if (future != null) {
                future.complete(null);
                return;
            }
            return;
        }
        switch (state) {
            case 6:
            case 8:
                loge("receive SCREEN_ON,...");
                notifyPowerOn();
                if (future != null) {
                    future.complete(null);
                    return;
                }
                return;
            case 7:
            case 9:
                loge("receive SCREEN_OFF,...");
                notifyPowerOff(future);
                return;
            default:
                if (future != null) {
                    future.complete(null);
                    return;
                }
                return;
        }
    }

    private void handleSuspendEnter() {
        logd("handleSuspendEnter");
        AirplaneModeHandler airplaneModeHandler = this.mAirplaneModeHandler;
        if (airplaneModeHandler != null) {
            airplaneModeHandler.handleSuspendEnter();
        }
    }

    private void notifyPowerOff(CompletableFuture<Void> future) {
        logd("notifyPowerOff");
        if (this.mAirplaneModeHandler != null) {
            setFuture(future);
            this.mAirplaneModeHandler.notifyPowerOff();
        } else if (future != null) {
            future.complete(null);
        }
    }

    private void notifyPowerOn() {
        logd("notifyPowerOn");
        AirplaneModeHandler airplaneModeHandler = this.mAirplaneModeHandler;
        if (airplaneModeHandler != null) {
            airplaneModeHandler.notifyPowerOn();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyPowerEventProcessingCompletion() {
        logd("notifyPowerEventProcessingCompletion");
        completeFuture();
    }

    private void setFuture(CompletableFuture<Void> future) {
        logd("setFuture: " + future);
        synchronized (this.mLock) {
            this.mFuture = future;
        }
    }

    private void completeFuture() {
        logd("completeFuture: " + this.mFuture);
        synchronized (this.mLock) {
            if (this.mFuture != null) {
                this.mFuture.complete(null);
                this.mFuture = null;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logd(String msg) {
        Slog.d(TAG, msg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logi(String msg) {
        Slog.i(TAG, msg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logw(String msg) {
        Slog.w(TAG, msg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loge(String msg) {
        Slog.e(TAG, msg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class AirplaneModeHandler extends Handler {
        private static final int AIRPLANE_MODE_OFF = 0;
        private static final int AIRPLANE_MODE_ON = 2;
        private static final int AIRPLANE_MODE_TURNING_OFF = 3;
        private static final int AIRPLANE_MODE_TURNING_ON = 1;
        private static final boolean DBG = true;
        private static final int MAX_NOTIFY_DELAY = 10000;
        private static final int MAX_RETRY = 4;
        private static final int MIN_BLUETOOTH_OFF_TIMEOUT = 400;
        private static final int MIN_NOTIFY_DELAY = 500;
        private static final int MIN_WIFI_OFF_TIMEOUT = 2000;
        private static final int MSG_AIRPLANE_MODE_CHANGED = 200;
        private static final int MSG_POWER_EVENT_PROCESSING_COMPLETE = 202;
        private static final int MSG_POWER_OFF = 100;
        private static final int MSG_POWER_ON = 101;
        private static final int MSG_RF_STATE_CHANGED = 201;
        private static final int MSG_TIMEOUT = 203;
        private static final String PROP_AIRPLANE_MODE_DURATION = "android.car.airplane_mode_duration";
        private static final String TAG = "AirplaneModeHandler";
        private int mAirplaneMode;
        private ContentObserver mAirplaneModeObserver;
        private IntentFilter mBluetoothIntentFilter;
        private BroadcastReceiver mBluetoothReceiver;
        private BluetoothState mBluetoothState;
        private int mDuration;
        private boolean mPowerOn;
        private RfState[] mRfStates;
        private IntentFilter mWifiApIntentFilter;
        private BroadcastReceiver mWifiApReceiver;
        private WifiApState mWifiApState;
        private IntentFilter mWifiIntentFilter;
        private IntentFilter mWifiP2pIntentFilter;
        private BroadcastReceiver mWifiP2pReceiver;
        private WifiP2pState mWifiP2pState;
        private BroadcastReceiver mWifiReceiver;
        private WifiState mWifiState;

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public class RfState {
            public static final int RF_ID_BLUETOOTH = 1;
            public static final int RF_ID_WIFI = 2;
            public static final int RF_ID_WIFI_AP = 3;
            public static final int RF_ID_WIFI_P2P = 4;
            public static final int RF_STATE_OFF = 2;
            public static final int RF_STATE_ON = 1;
            public static final int RF_STATE_UNKNOWN = 0;
            private Context context;
            private int id;
            private String name;
            private int state;
            private final Object lock = new Object();
            private int count = 0;
            @GuardedBy({"lock"})
            private boolean needWaitClosed = false;

            public RfState(int id, int state, String name, Context context) {
                this.id = id;
                this.state = state;
                this.name = name;
                this.context = context;
            }

            public void init() {
            }

            public void close() {
                AirplaneModeService.this.loge("close empty");
            }

            public void closeWithTimeout() {
                closeWithTimeout(AirplaneModeHandler.MIN_WIFI_OFF_TIMEOUT);
            }

            public void closeWithTimeout(int timeoutInMs) {
                AirplaneModeService airplaneModeService = AirplaneModeService.this;
                airplaneModeService.logd("closeWithTimeout " + timeoutInMs + " ms");
                synchronized (this.lock) {
                    this.needWaitClosed = true;
                }
                close();
                waitClosed(timeoutInMs);
                AirplaneModeService.this.logd("closeWithTimeout done");
            }

            public Context getContext() {
                return this.context;
            }

            public String getName() {
                return this.name;
            }

            public int getId() {
                return this.id;
            }

            public int getRfState() {
                return this.state;
            }

            public void setRfState(int state) {
                this.state = state;
                setCount(state);
                if (state == 2) {
                    notifyClosed();
                }
            }

            public boolean isOn() {
                return this.state == 1;
            }

            public String getRfStateString(int state) {
                if (state != 1) {
                    if (state == 2) {
                        return "off";
                    }
                    return "unknown";
                }
                return "on";
            }

            public void increaseCount() {
                this.count++;
            }

            public void decreaseCount() {
                int i = this.count;
                if (i > 0) {
                    this.count = i - 1;
                }
            }

            public int getCount() {
                return this.count;
            }

            public void setCount(int state) {
                if (state == 1) {
                    increaseCount();
                } else if (state == 2) {
                    decreaseCount();
                }
            }

            private void waitClosed(int timeoutInMs) {
                synchronized (this.lock) {
                    try {
                        this.lock.wait(timeoutInMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    this.needWaitClosed = false;
                }
            }

            private void notifyClosed() {
                synchronized (this.lock) {
                    if (this.needWaitClosed) {
                        this.lock.notify();
                    }
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public class BluetoothState extends RfState {
            private final BluetoothAdapter mBluetoothAdapter;

            public BluetoothState(Context context) {
                super(1, 2, "Bluetooth", context);
                this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void init() {
                int state = getBluetoothState();
                int curRfState = mapBluetoothState2RfState(state);
                setRfState(curRfState);
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void close() {
                if (this.mBluetoothAdapter != null) {
                    AirplaneModeService.this.logd("disable Bluetooth");
                    this.mBluetoothAdapter.disable();
                }
            }

            public int mapBluetoothState2RfState(int state) {
                if (state != 10) {
                    if (state == 12) {
                        return 1;
                    }
                    return 0;
                }
                return 2;
            }

            public int getBluetoothState() {
                BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
                if (bluetoothAdapter != null) {
                    return bluetoothAdapter.getState();
                }
                return Integer.MIN_VALUE;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public class WifiState extends RfState {
            private final WifiManager mWifiManager;

            public WifiState(Context context) {
                super(2, 2, "Wifi", context);
                this.mWifiManager = (WifiManager) context.getSystemService("wifi");
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void init() {
                int state = getWifiState();
                int curRfState = mapWifiState2RfState(state);
                setRfState(curRfState);
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void close() {
                if (this.mWifiManager != null) {
                    AirplaneModeService.this.logd("disable Wifi");
                    this.mWifiManager.setWifiEnabled(false);
                }
            }

            public int mapWifiState2RfState(int state) {
                if (state != 1) {
                    if (state == 3) {
                        return 1;
                    }
                    return 0;
                }
                return 2;
            }

            public int getWifiState() {
                WifiManager wifiManager = this.mWifiManager;
                if (wifiManager != null) {
                    return wifiManager.getWifiState();
                }
                return 4;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public class WifiApState extends RfState {
            private final WifiManager mWifiManager;

            public WifiApState(Context context) {
                super(3, 2, "WifiAp", context);
                this.mWifiManager = (WifiManager) context.getSystemService("wifi");
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void init() {
                int state = getWifiApState();
                int curRfState = mapWifiApState2RfState(state);
                setRfState(curRfState);
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void close() {
                if (this.mWifiManager != null) {
                    AirplaneModeService.this.logd("disable WifiAp");
                    this.mWifiManager.stopSoftAp();
                }
            }

            public int mapWifiApState2RfState(int state) {
                if (state != 11) {
                    if (state == 13) {
                        return 1;
                    }
                    return 0;
                }
                return 2;
            }

            public int getWifiApState() {
                WifiManager wifiManager = this.mWifiManager;
                if (wifiManager != null) {
                    return wifiManager.getWifiApState();
                }
                return 11;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public class WifiP2pState extends RfState {
            private final WifiP2pManager mWifiP2pManager;

            public WifiP2pState(Context context) {
                super(4, 2, "WifiP2p", context);
                this.mWifiP2pManager = (WifiP2pManager) context.getSystemService("wifip2p");
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void init() {
                int curRfState = mapWifiP2pState2RfState(1);
                setRfState(curRfState);
            }

            @Override // com.android.car.AirplaneModeService.AirplaneModeHandler.RfState
            public void close() {
                if (this.mWifiP2pManager != null) {
                    AirplaneModeService.this.logd("disable WifiP2p");
                    WifiP2pManager.Channel channel = new WifiP2pManager.Channel(getContext(), AirplaneModeHandler.this.getLooper(), null, null, this.mWifiP2pManager);
                    channel.close();
                }
            }

            public int mapWifiP2pState2RfState(int state) {
                if (state != 1) {
                    if (state == 2) {
                        return 1;
                    }
                    return 0;
                }
                return 2;
            }
        }

        public AirplaneModeHandler(Looper looper) {
            super(looper);
            this.mPowerOn = true;
            this.mAirplaneMode = 0;
            this.mDuration = MIN_NOTIFY_DELAY;
            this.mAirplaneModeObserver = new ContentObserver(new Handler()) { // from class: com.android.car.AirplaneModeService.AirplaneModeHandler.1
                @Override // android.database.ContentObserver
                public void onChange(boolean selfChange) {
                    AirplaneModeHandler.this.notifyAirplaneModeChanged();
                }
            };
            this.mBluetoothReceiver = new BroadcastReceiver() { // from class: com.android.car.AirplaneModeService.AirplaneModeHandler.2
                @Override // android.content.BroadcastReceiver
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                    if (state == 12) {
                        AirplaneModeHandler.this.notifyBluetoothStateChanged(true);
                    } else if (state == 10) {
                        AirplaneModeHandler.this.notifyBluetoothStateChanged(false);
                    }
                }
            };
            this.mWifiReceiver = new BroadcastReceiver() { // from class: com.android.car.AirplaneModeService.AirplaneModeHandler.3
                @Override // android.content.BroadcastReceiver
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra("wifi_state", 4);
                    AirplaneModeService airplaneModeService = AirplaneModeService.this;
                    airplaneModeService.logd("AirplaneModeHandler wifi onReceive, state: " + state);
                    if (state == 2) {
                        AirplaneModeHandler.this.notifyWifiStateChanged(true);
                    } else if (state == 1) {
                        AirplaneModeHandler.this.notifyWifiStateChanged(false);
                    }
                }
            };
            this.mWifiApReceiver = new BroadcastReceiver() { // from class: com.android.car.AirplaneModeService.AirplaneModeHandler.4
                @Override // android.content.BroadcastReceiver
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra("wifi_state", 11);
                    if (state == 13) {
                        AirplaneModeHandler.this.notifyWifiApStateChanged(true);
                    } else if (state == 11) {
                        AirplaneModeHandler.this.notifyWifiApStateChanged(false);
                    }
                }
            };
            this.mWifiP2pReceiver = new BroadcastReceiver() { // from class: com.android.car.AirplaneModeService.AirplaneModeHandler.5
                @Override // android.content.BroadcastReceiver
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra("wifi_p2p_state", -1);
                    if (state == 2) {
                        AirplaneModeHandler.this.notifyWifiP2pStateChanged(true);
                    } else if (state == 1) {
                        AirplaneModeHandler.this.notifyWifiP2pStateChanged(false);
                    }
                }
            };
            AirplaneModeService.this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("airplane_mode_on"), true, this.mAirplaneModeObserver);
            this.mAirplaneMode = getAirplaneMode();
            AirplaneModeService.this.logd("AirplaneModeHandler mAirplaneMode: " + this.mAirplaneMode + " (" + mapAirplaneMode2String(this.mAirplaneMode) + ")");
            int i = this.mAirplaneMode;
            if (i == 0 || i == 3) {
                this.mPowerOn = true;
            }
            initIntentFilter();
            initRfStates();
        }

        private void initIntentFilter() {
            this.mBluetoothIntentFilter = new IntentFilter();
            this.mBluetoothIntentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
            AirplaneModeService.this.mContext.registerReceiver(this.mBluetoothReceiver, this.mBluetoothIntentFilter);
            this.mWifiIntentFilter = new IntentFilter();
            this.mWifiIntentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
            AirplaneModeService.this.mContext.registerReceiver(this.mWifiReceiver, this.mWifiIntentFilter);
            this.mWifiApIntentFilter = new IntentFilter();
            this.mWifiApIntentFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
            AirplaneModeService.this.mContext.registerReceiver(this.mWifiApReceiver, this.mWifiApIntentFilter);
            this.mWifiP2pIntentFilter = new IntentFilter();
            this.mWifiP2pIntentFilter.addAction("android.net.wifi.p2p.STATE_CHANGED");
            AirplaneModeService.this.mContext.registerReceiver(this.mWifiP2pReceiver, this.mWifiP2pIntentFilter);
        }

        private void initRfStates() {
            RfState[] rfStateArr;
            this.mBluetoothState = new BluetoothState(AirplaneModeService.this.mContext);
            this.mWifiState = new WifiState(AirplaneModeService.this.mContext);
            this.mWifiApState = new WifiApState(AirplaneModeService.this.mContext);
            this.mWifiP2pState = new WifiP2pState(AirplaneModeService.this.mContext);
            List<RfState> allStates = new ArrayList<>(Arrays.asList(this.mBluetoothState, this.mWifiState, this.mWifiApState, this.mWifiP2pState));
            this.mRfStates = (RfState[]) allStates.toArray(new RfState[0]);
            for (RfState state : this.mRfStates) {
                state.init();
            }
        }

        public void handleSuspendEnter() {
            turnWifiAllOff();
        }

        public void notifyPowerOff() {
            sendEmptyMessage(100);
        }

        public void notifyPowerOn() {
            sendEmptyMessage(101);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void notifyAirplaneModeChanged() {
            AirplaneModeService.this.logd("notifyAirplaneModeChanged ");
            sendEmptyMessage(200);
        }

        private void notifyRfStateChanged(int id, int state) {
            Message msg = obtainMessage(MSG_RF_STATE_CHANGED, id, state);
            sendMessage(msg);
        }

        private void postponePowerEventProcessingCompletion(int delay) {
            sendEmptyMessageDelayed(202, delay);
        }

        public void notifyBluetoothStateChanged(boolean on) {
            notifyRfStateChanged(1, on ? 1 : 2);
        }

        public void notifyWifiStateChanged(boolean on) {
            notifyRfStateChanged(2, on ? 1 : 2);
        }

        public void notifyWifiApStateChanged(boolean on) {
            notifyRfStateChanged(3, on ? 1 : 2);
        }

        public void notifyWifiP2pStateChanged(boolean on) {
            notifyRfStateChanged(4, on ? 1 : 2);
        }

        private void turnWifiAllOff() {
            int retry = 0;
            int delay = 0;
            while (true) {
                int retry2 = retry + 1;
                if (retry >= 4) {
                    break;
                }
                AirplaneModeService airplaneModeService = AirplaneModeService.this;
                airplaneModeService.logd("turnWifiAllOff: retry:" + retry2 + ",wifi cnt:" + this.mWifiState.getCount() + ",ap cnt:" + this.mWifiApState.getCount() + ",p2p cnt:" + this.mWifiP2pState.getCount());
                if (this.mWifiState.getCount() > 0 || this.mWifiApState.getCount() > 0 || this.mWifiP2pState.getCount() > 0) {
                    if (this.mWifiState.getCount() > 0) {
                        this.mWifiState.closeWithTimeout();
                    }
                    if (this.mWifiApState.getCount() > 0) {
                        this.mWifiApState.closeWithTimeout();
                    }
                    if (this.mWifiP2pState.getCount() > 0) {
                        this.mWifiP2pState.closeWithTimeout();
                    }
                    delay = MIN_WIFI_OFF_TIMEOUT;
                    AirplaneModeService.this.logd("turnWifiAllOff done");
                    retry = retry2;
                } else if (getPersistedScanAlwaysAvailable()) {
                    setPersistedScanDisabled();
                }
            }
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            }
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 100) {
                handlePowerOff();
            } else if (i == 101) {
                handlePowerOn();
            } else {
                switch (i) {
                    case 200:
                        handleAirplaneModeChanged();
                        return;
                    case MSG_RF_STATE_CHANGED /* 201 */:
                        handleRfStateChanged(msg.arg1, msg.arg2);
                        return;
                    case 202:
                        handlePowerEventProcessingComplete();
                        return;
                    case 203:
                        handleTimeout();
                        return;
                    default:
                        return;
                }
            }
        }

        private void handlePowerOff() {
            AirplaneModeService airplaneModeService = AirplaneModeService.this;
            airplaneModeService.logd("handlePowerOff airplane mode: " + this.mAirplaneMode + " (" + mapAirplaneMode2String(this.mAirplaneMode) + ")");
            this.mDuration = calculateDuration();
            if (!this.mPowerOn) {
                AirplaneModeService.this.logw("handlePowerOff already power off");
                postponePowerEventProcessingCompletion(this.mDuration);
                return;
            }
            this.mPowerOn = false;
            if (this.mAirplaneMode != 0) {
                if (isRfOff()) {
                    AirplaneModeService.this.logw("handlePowerOff ignore since airplane is on and RF is off");
                    postponePowerEventProcessingCompletion(this.mDuration);
                    return;
                }
                AirplaneModeService.this.loge("handlePowerOff invalid airplane on and RF on");
                sendEmptyMessageDelayed(203, 10000L);
                closeRf();
            } else if (isRfOff()) {
                postponePowerEventProcessingCompletion(this.mDuration);
            } else {
                setAirplaneModeOn(true);
                this.mAirplaneMode = 1;
                sendEmptyMessageDelayed(203, 10000L);
            }
        }

        private void handlePowerOn() {
            if (this.mPowerOn) {
                AirplaneModeService.this.logw("handlePowerOn already power on");
                return;
            }
            this.mPowerOn = true;
            AirplaneModeService airplaneModeService = AirplaneModeService.this;
            airplaneModeService.logi("handlePowerOn airplane mode: " + this.mAirplaneMode + " (" + mapAirplaneMode2String(this.mAirplaneMode) + ")");
            int i = this.mAirplaneMode;
            if (i != 2 && i != 1) {
                AirplaneModeService.this.loge("handlePowerOn ignore due to invalid airplane mode");
                return;
            }
            setAirplaneModeOn(false);
            this.mAirplaneMode = 3;
        }

        private void handleAirplaneModeChanged() {
            int newMode = getAirplaneMode();
            AirplaneModeService airplaneModeService = AirplaneModeService.this;
            airplaneModeService.logi("handleAirplaneModeChanged " + mapAirplaneMode2String(newMode));
            this.mAirplaneMode = newMode;
        }

        private void handleRfStateChanged(int id, int state) {
            updateRfState(id, state);
            if (!this.mPowerOn && isRfOff()) {
                removeMessages(203);
                AirplaneModeService airplaneModeService = AirplaneModeService.this;
                airplaneModeService.logi("postpone notifying completion, duration: " + this.mDuration + " ms");
                postponePowerEventProcessingCompletion(this.mDuration);
            }
        }

        private void handlePowerEventProcessingComplete() {
            AirplaneModeService.this.logi("handlePowerEventProcessingComplete");
            if (!this.mPowerOn && isRfOff()) {
                AirplaneModeService.this.notifyPowerEventProcessingCompletion();
            }
        }

        private void handleTimeout() {
            AirplaneModeService.this.loge("handleTimeout ");
            AirplaneModeService.this.notifyPowerEventProcessingCompletion();
        }

        private int calculateDuration() {
            int duration = SystemProperties.getInt(PROP_AIRPLANE_MODE_DURATION, (int) MIN_NOTIFY_DELAY);
            if (duration > 9500) {
                duration = 9500;
            }
            if (this.mBluetoothState.isOn()) {
                duration = Math.max(duration, 400);
            }
            if (this.mWifiState.isOn()) {
                duration = Math.max(duration, (int) MIN_WIFI_OFF_TIMEOUT);
            }
            if (this.mWifiApState.isOn()) {
                duration = Math.max(duration, (int) MIN_WIFI_OFF_TIMEOUT);
            }
            if (this.mWifiP2pState.isOn()) {
                duration = Math.max(duration, (int) MIN_WIFI_OFF_TIMEOUT);
            }
            AirplaneModeService.this.logd("calculateDuration: " + duration + " ms");
            return duration;
        }

        private void updateRfState(int id, int state) {
            if (id == 1) {
                AirplaneModeService airplaneModeService = AirplaneModeService.this;
                airplaneModeService.logi("Bluetooth " + this.mBluetoothState.getRfStateString(state));
                this.mBluetoothState.setRfState(state);
            } else if (id == 2) {
                AirplaneModeService airplaneModeService2 = AirplaneModeService.this;
                airplaneModeService2.logi("Wifi " + this.mWifiState.getRfStateString(state));
                this.mWifiState.setRfState(state);
            } else if (id == 3) {
                AirplaneModeService airplaneModeService3 = AirplaneModeService.this;
                airplaneModeService3.logi("WifiAp " + this.mWifiApState.getRfStateString(state));
                this.mWifiApState.setRfState(state);
            } else if (id == 4) {
                AirplaneModeService airplaneModeService4 = AirplaneModeService.this;
                airplaneModeService4.logi("WifiP2p " + this.mWifiP2pState.getRfStateString(state));
                this.mWifiP2pState.setRfState(state);
            } else {
                AirplaneModeService airplaneModeService5 = AirplaneModeService.this;
                airplaneModeService5.loge("updateRfState unknown id: " + id + ", state: " + state);
            }
        }

        private boolean isRfOff() {
            RfState[] rfStateArr;
            for (RfState state : this.mRfStates) {
                if (state.isOn()) {
                    return false;
                }
            }
            return true;
        }

        private void closeRf() {
            RfState[] rfStateArr;
            AirplaneModeService.this.logi("close RF");
            for (RfState state : this.mRfStates) {
                state.close();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setAirplaneModeOn(boolean enable) {
            AirplaneModeService airplaneModeService = AirplaneModeService.this;
            StringBuilder sb = new StringBuilder();
            sb.append("set airplane mode ");
            sb.append(enable ? "on" : "off");
            airplaneModeService.logi(sb.toString());
            Settings.Global.putInt(AirplaneModeService.this.mContext.getContentResolver(), "airplane_mode_on", enable ? 1 : 0);
            Intent intent = new Intent("android.intent.action.AIRPLANE_MODE");
            intent.putExtra("state", enable);
            AirplaneModeService.this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }

        private int getAirplaneMode() {
            return isAirplaneModeOn() ? 2 : 0;
        }

        public boolean isAirplaneModeOn() {
            return Settings.Global.getInt(AirplaneModeService.this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
        }

        public boolean getPersistedScanAlwaysAvailable() {
            return Settings.Global.getInt(AirplaneModeService.this.mContext.getContentResolver(), "wifi_scan_always_enabled", 0) == 1;
        }

        public void setPersistedScanDisabled() {
            Settings.Global.putInt(AirplaneModeService.this.mContext.getContentResolver(), "wifi_scan_always_enabled", 0);
        }

        private String mapAirplaneMode2String(int mode) {
            if (mode != 0) {
                if (mode != 1) {
                    if (mode != 2) {
                        if (mode == 3) {
                            return "turning off";
                        }
                        return "unknown";
                    }
                    return "on";
                }
                return "turning on";
            }
            return "off";
        }

        public void dump(PrintWriter writer) {
            RfState[] rfStateArr;
            writer.println("     AirplaneModeHandler" + toString());
            writer.println("          RfState(size=" + this.mRfStates.length + ")");
            for (RfState rfState : this.mRfStates) {
                writer.println("               RfState(" + rfState.getName() + ", state=" + rfState.getRfStateString(rfState.state) + ")");
            }
        }
    }
}
