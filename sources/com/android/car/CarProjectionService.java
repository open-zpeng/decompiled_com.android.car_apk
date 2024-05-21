package com.android.car;

import android.app.ActivityOptions;
import android.bluetooth.BluetoothDevice;
import android.car.CarProjectionManager;
import android.car.ICarProjection;
import android.car.ICarProjectionKeyEventHandler;
import android.car.ICarProjectionStatusListener;
import android.car.projection.ProjectionOptions;
import android.car.projection.ProjectionStatus;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;
import com.android.car.BinderInterfaceContainer;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class CarProjectionService extends ICarProjection.Stub implements CarServiceBase, BinderInterfaceContainer.BinderEventHandler<ICarProjectionKeyEventHandler>, CarProjectionManager.ProjectionKeyEventHandler {
    private static final boolean DBG = true;
    private static final String TAG = "CAR.PROJECTION";
    private static final int WIFI_MODE_LOCALONLY = 2;
    private static final int WIFI_MODE_TETHERED = 1;
    private String mApBssid;
    private boolean mBound;
    private final CarBluetoothService mCarBluetoothService;
    private final CarInputService mCarInputService;
    private final Context mContext;
    @GuardedBy({"mLock"})
    private String mCurrentProjectionPackage;
    private final Handler mHandler;
    @GuardedBy({"mLock"})
    private final ProjectionKeyEventHandlerContainer mKeyEventHandlers;
    @GuardedBy({"mLock"})
    private WifiManager.LocalOnlyHotspotReservation mLocalOnlyHotspotReservation;
    @GuardedBy({"mLock"})
    private ProjectionOptions mProjectionOptions;
    private Intent mRegisteredService;
    @GuardedBy({"mLock"})
    private ProjectionSoftApCallback mSoftApCallback;
    private final WifiManager mWifiManager;
    private int mWifiMode;
    @GuardedBy({"mLock"})
    private WifiScanner mWifiScanner;
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final HashMap<IBinder, WirelessClient> mWirelessClients = new HashMap<>();
    @GuardedBy({"mLock"})
    private final HashMap<IBinder, ProjectionReceiverClient> mProjectionReceiverClients = new HashMap<>();
    @GuardedBy({"mLock"})
    private int mCurrentProjectionState = 0;
    private final BinderInterfaceContainer<ICarProjectionStatusListener> mProjectionStatusListeners = new BinderInterfaceContainer<>();
    private final ServiceConnection mConnection = new ServiceConnection() { // from class: com.android.car.CarProjectionService.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName className, IBinder service) {
            synchronized (CarProjectionService.this.mLock) {
                CarProjectionService.this.mBound = true;
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName className) {
            Slog.w("CAR.PROJECTION", "Service disconnected: " + className);
            synchronized (CarProjectionService.this.mLock) {
                CarProjectionService.this.mRegisteredService = null;
            }
            CarProjectionService.this.unbindServiceIfBound();
        }
    };
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.car.CarProjectionService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            int currState = intent.getIntExtra("wifi_state", 11);
            int prevState = intent.getIntExtra("previous_wifi_state", 11);
            int errorCode = intent.getIntExtra("wifi_ap_error_code", 0);
            String ifaceName = intent.getStringExtra("wifi_ap_interface_name");
            int mode = intent.getIntExtra("wifi_ap_mode", -1);
            CarProjectionService.this.handleWifiApStateChange(currState, prevState, errorCode, ifaceName, mode);
        }
    };

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarProjectionService(Context context, Handler handler, CarInputService carInputService, CarBluetoothService carBluetoothService) {
        this.mContext = context;
        this.mHandler = handler == null ? new Handler() : handler;
        this.mCarInputService = carInputService;
        this.mCarBluetoothService = carBluetoothService;
        this.mKeyEventHandlers = new ProjectionKeyEventHandlerContainer(this);
        this.mWifiManager = (WifiManager) context.getSystemService(WifiManager.class);
        Resources res = this.mContext.getResources();
        setAccessPointTethering(res.getBoolean(R.bool.config_projectionAccessPointTethering));
    }

    public void registerProjectionRunner(Intent serviceIntent) {
        ICarImpl.assertProjectionPermission(this.mContext);
        synchronized (this.mLock) {
            if (serviceIntent.filterEquals(this.mRegisteredService) && this.mBound) {
                return;
            }
            if (this.mRegisteredService != null) {
                Slog.w("CAR.PROJECTION", "Registering new service[" + serviceIntent + "] while old service[" + this.mRegisteredService + "] is still running");
            }
            unbindServiceIfBound();
            bindToService(serviceIntent);
        }
    }

    public void unregisterProjectionRunner(Intent serviceIntent) {
        ICarImpl.assertProjectionPermission(this.mContext);
        synchronized (this.mLock) {
            if (!serviceIntent.filterEquals(this.mRegisteredService)) {
                Slog.w("CAR.PROJECTION", "Request to unbind unregistered service[" + serviceIntent + "]. Registered service[" + this.mRegisteredService + "]");
                return;
            }
            this.mRegisteredService = null;
            unbindServiceIfBound();
        }
    }

    private void bindToService(Intent serviceIntent) {
        synchronized (this.mLock) {
            this.mRegisteredService = serviceIntent;
        }
        UserHandle userHandle = UserHandle.getUserHandleForUid(Binder.getCallingUid());
        this.mContext.bindServiceAsUser(serviceIntent, this.mConnection, 1, userHandle);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unbindServiceIfBound() {
        synchronized (this.mLock) {
            if (this.mBound) {
                this.mBound = false;
                this.mRegisteredService = null;
                this.mContext.unbindService(this.mConnection);
            }
        }
    }

    public void registerKeyEventHandler(ICarProjectionKeyEventHandler eventHandler, byte[] eventMask) {
        ICarImpl.assertProjectionPermission(this.mContext);
        BitSet events = BitSet.valueOf(eventMask);
        Preconditions.checkArgument(events.length() <= 8, "Unknown handled event");
        synchronized (this.mLock) {
            ProjectionKeyEventHandler info = this.mKeyEventHandlers.get(eventHandler);
            if (info == null) {
                this.mKeyEventHandlers.addBinderInterface(new ProjectionKeyEventHandler(this.mKeyEventHandlers, eventHandler, events));
            } else {
                info.setHandledEvents(events);
            }
            updateInputServiceHandlerLocked();
        }
    }

    public void unregisterKeyEventHandler(ICarProjectionKeyEventHandler eventHandler) {
        ICarImpl.assertProjectionPermission(this.mContext);
        synchronized (this.mLock) {
            this.mKeyEventHandlers.removeBinder(eventHandler);
            updateInputServiceHandlerLocked();
        }
    }

    public void startProjectionAccessPoint(Messenger messenger, IBinder binder) throws RemoteException {
        ICarImpl.assertProjectionPermission(this.mContext);
        registerWirelessClient(WirelessClient.of(messenger, binder));
        startAccessPoint();
    }

    public void stopProjectionAccessPoint(IBinder token) {
        ICarImpl.assertProjectionPermission(this.mContext);
        Slog.i("CAR.PROJECTION", "Received stop access point request from " + token);
        synchronized (this.mLock) {
            if (!unregisterWirelessClientLocked(token)) {
                Slog.w("CAR.PROJECTION", "Client " + token + " was not registered");
                return;
            }
            boolean shouldReleaseAp = this.mWirelessClients.isEmpty();
            if (shouldReleaseAp) {
                stopAccessPoint();
            }
        }
    }

    public int[] getAvailableWifiChannels(int band) {
        WifiScanner scanner;
        ICarImpl.assertProjectionPermission(this.mContext);
        synchronized (this.mLock) {
            if (this.mWifiScanner == null) {
                this.mWifiScanner = (WifiScanner) this.mContext.getSystemService(WifiScanner.class);
            }
            scanner = this.mWifiScanner;
        }
        if (scanner == null) {
            Slog.w("CAR.PROJECTION", "Unable to get WifiScanner");
            return new int[0];
        }
        List<Integer> channels = scanner.getAvailableChannels(band);
        if (channels == null || channels.isEmpty()) {
            Slog.w("CAR.PROJECTION", "WifiScanner reported no available channels");
            return new int[0];
        }
        int[] array = new int[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            array[i] = channels.get(i).intValue();
        }
        return array;
    }

    public boolean requestBluetoothProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        Slog.d("CAR.PROJECTION", "requestBluetoothProfileInhibit device=" + device + " profile=" + profile + " from uid " + Binder.getCallingUid());
        ICarImpl.assertProjectionPermission(this.mContext);
        try {
            if (device == null) {
                throw new NullPointerException("Device must not be null");
            }
            if (token == null) {
                throw new NullPointerException("Token must not be null");
            }
            return this.mCarBluetoothService.requestProfileInhibit(device, profile, token);
        } catch (RuntimeException e) {
            Slog.e("CAR.PROJECTION", "Error in requestBluetoothProfileInhibit", e);
            throw e;
        }
    }

    public boolean releaseBluetoothProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        Slog.d("CAR.PROJECTION", "releaseBluetoothProfileInhibit device=" + device + " profile=" + profile + " from uid " + Binder.getCallingUid());
        ICarImpl.assertProjectionPermission(this.mContext);
        try {
            if (device == null) {
                throw new NullPointerException("Device must not be null");
            }
            if (token == null) {
                throw new NullPointerException("Token must not be null");
            }
            return this.mCarBluetoothService.releaseProfileInhibit(device, profile, token);
        } catch (RuntimeException e) {
            Slog.e("CAR.PROJECTION", "Error in releaseBluetoothProfileInhibit", e);
            throw e;
        }
    }

    public void updateProjectionStatus(ProjectionStatus status, IBinder token) throws RemoteException {
        Slog.d("CAR.PROJECTION", "updateProjectionStatus, status: " + status + ", token: " + token);
        ICarImpl.assertProjectionPermission(this.mContext);
        String packageName = status.getPackageName();
        int callingUid = Binder.getCallingUid();
        int userHandleId = Binder.getCallingUserHandle().getIdentifier();
        try {
            int packageUid = this.mContext.getPackageManager().getPackageUidAsUser(packageName, userHandleId);
            if (callingUid != packageUid) {
                throw new SecurityException("UID " + callingUid + " cannot update status for package " + packageName);
            }
            synchronized (this.mLock) {
                ProjectionReceiverClient client = getOrCreateProjectionReceiverClientLocked(token);
                client.mProjectionStatus = status;
                if (status.isActive() || ((status.getState() == 1 && this.mCurrentProjectionState == 0) || TextUtils.equals(packageName, this.mCurrentProjectionPackage))) {
                    this.mCurrentProjectionState = status.getState();
                    this.mCurrentProjectionPackage = packageName;
                }
            }
            notifyProjectionStatusChanged(null);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("Package " + packageName + " does not exist", e);
        }
    }

    public void registerProjectionStatusListener(ICarProjectionStatusListener listener) throws RemoteException {
        ICarImpl.assertProjectionStatusPermission(this.mContext);
        this.mProjectionStatusListeners.addBinder(listener);
        notifyProjectionStatusChanged(listener);
    }

    public void unregisterProjectionStatusListener(ICarProjectionStatusListener listener) throws RemoteException {
        ICarImpl.assertProjectionStatusPermission(this.mContext);
        this.mProjectionStatusListeners.removeBinder(listener);
    }

    private ProjectionReceiverClient getOrCreateProjectionReceiverClientLocked(final IBinder token) throws RemoteException {
        ProjectionReceiverClient client = this.mProjectionReceiverClients.get(token);
        if (client == null) {
            ProjectionReceiverClient client2 = new ProjectionReceiverClient(new IBinder.DeathRecipient() { // from class: com.android.car.-$$Lambda$CarProjectionService$0Y_gaNrLudUElLWvh51qbk6sLUI
                @Override // android.os.IBinder.DeathRecipient
                public final void binderDied() {
                    CarProjectionService.this.lambda$getOrCreateProjectionReceiverClientLocked$0$CarProjectionService(token);
                }
            });
            token.linkToDeath(client2.mDeathRecipient, 0);
            this.mProjectionReceiverClients.put(token, client2);
            return client2;
        }
        return client;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: unregisterProjectionReceiverClient */
    public void lambda$getOrCreateProjectionReceiverClientLocked$0$CarProjectionService(IBinder token) {
        synchronized (this.mLock) {
            ProjectionReceiverClient client = this.mProjectionReceiverClients.remove(token);
            if (client == null) {
                Slog.w("CAR.PROJECTION", "Projection receiver client for token " + token + " doesn't exist");
                return;
            }
            token.unlinkToDeath(client.mDeathRecipient, 0);
            if (TextUtils.equals(client.mProjectionStatus.getPackageName(), this.mCurrentProjectionPackage)) {
                this.mCurrentProjectionPackage = null;
                this.mCurrentProjectionState = 0;
            }
        }
    }

    private void notifyProjectionStatusChanged(ICarProjectionStatusListener singleListenerToNotify) throws RemoteException {
        int currentState;
        String currentPackage;
        List<ProjectionStatus> statuses = new ArrayList<>();
        synchronized (this.mLock) {
            for (ProjectionReceiverClient client : this.mProjectionReceiverClients.values()) {
                statuses.add(client.mProjectionStatus);
            }
            currentState = this.mCurrentProjectionState;
            currentPackage = this.mCurrentProjectionPackage;
        }
        Slog.d("CAR.PROJECTION", "Notify projection status change, state: " + currentState + ", pkg: " + currentPackage + ", listeners: " + this.mProjectionStatusListeners.size() + ", listenerToNotify: " + singleListenerToNotify);
        if (singleListenerToNotify == null) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionStatusListener> listener : this.mProjectionStatusListeners.getInterfaces()) {
                try {
                    listener.binderInterface.onProjectionStatusChanged(currentState, currentPackage, statuses);
                } catch (RemoteException ex) {
                    Slog.e("CAR.PROJECTION", "Error calling to projection status listener", ex);
                }
            }
            return;
        }
        singleListenerToNotify.onProjectionStatusChanged(currentState, currentPackage, statuses);
    }

    public Bundle getProjectionOptions() {
        ICarImpl.assertProjectionPermission(this.mContext);
        synchronized (this.mLock) {
            if (this.mProjectionOptions == null) {
                this.mProjectionOptions = createProjectionOptionsBuilder().build();
            }
        }
        return this.mProjectionOptions.toBundle();
    }

    private ProjectionOptions.Builder createProjectionOptionsBuilder() {
        Resources res = this.mContext.getResources();
        ProjectionOptions.Builder builder = ProjectionOptions.builder();
        ActivityOptions activityOptions = createActivityOptions(res);
        if (activityOptions != null) {
            builder.setProjectionActivityOptions(activityOptions);
        }
        String consentActivity = res.getString(R.string.config_projectionConsentActivity);
        if (!TextUtils.isEmpty(consentActivity)) {
            builder.setConsentActivity(ComponentName.unflattenFromString(consentActivity));
        }
        builder.setUiMode(res.getInteger(R.integer.config_projectionUiMode));
        return builder;
    }

    private static ActivityOptions createActivityOptions(Resources res) {
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        boolean changed = false;
        int displayId = res.getInteger(R.integer.config_projectionActivityDisplayId);
        if (displayId != -1) {
            activityOptions.setLaunchDisplayId(displayId);
            changed = true;
        }
        int[] rawBounds = res.getIntArray(R.array.config_projectionActivityLaunchBounds);
        if (rawBounds != null && rawBounds.length == 4) {
            Rect bounds = new Rect(rawBounds[0], rawBounds[1], rawBounds[2], rawBounds[3]);
            activityOptions.setLaunchBounds(bounds);
            changed = true;
        }
        if (changed) {
            return activityOptions;
        }
        return null;
    }

    private void startAccessPoint() {
        synchronized (this.mLock) {
            int i = this.mWifiMode;
            if (i == 1) {
                startTetheredApLocked();
            } else if (i == 2) {
                startLocalOnlyApLocked();
            } else {
                Slog.e("CAR.PROJECTION", "Unexpected Access Point mode during starting: " + this.mWifiMode);
            }
        }
    }

    private void stopAccessPoint() {
        sendApStopped();
        synchronized (this.mLock) {
            int i = this.mWifiMode;
            if (i == 1) {
                stopTetheredApLocked();
            } else if (i == 2) {
                stopLocalOnlyApLocked();
            } else {
                Slog.e("CAR.PROJECTION", "Unexpected Access Point mode during stopping : " + this.mWifiMode);
            }
        }
    }

    private void startTetheredApLocked() {
        Slog.d("CAR.PROJECTION", "startTetheredApLocked");
        if (this.mSoftApCallback == null) {
            this.mSoftApCallback = new ProjectionSoftApCallback();
            this.mWifiManager.registerSoftApCallback(this.mSoftApCallback, this.mHandler);
            ensureApConfiguration();
        }
        if (!this.mWifiManager.startSoftAp(null)) {
            if (this.mWifiManager.getWifiApState() != 13) {
                Slog.e("CAR.PROJECTION", "Failed to start soft AP");
                sendApFailed(2);
                return;
            }
            sendApStarted(this.mWifiManager.getWifiApConfiguration());
        }
    }

    private void stopTetheredApLocked() {
        Slog.d("CAR.PROJECTION", "stopTetheredAp");
        ProjectionSoftApCallback projectionSoftApCallback = this.mSoftApCallback;
        if (projectionSoftApCallback != null) {
            this.mWifiManager.unregisterSoftApCallback(projectionSoftApCallback);
            this.mSoftApCallback = null;
            if (!this.mWifiManager.stopSoftAp()) {
                Slog.w("CAR.PROJECTION", "Failed to request soft AP to stop.");
            }
        }
    }

    private void startLocalOnlyApLocked() {
        if (this.mLocalOnlyHotspotReservation != null) {
            Slog.i("CAR.PROJECTION", "Local-only hotspot is already registered.");
            sendApStarted(this.mLocalOnlyHotspotReservation.getWifiConfiguration());
            return;
        }
        Slog.i("CAR.PROJECTION", "Requesting to start local-only hotspot.");
        this.mWifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() { // from class: com.android.car.CarProjectionService.3
            @Override // android.net.wifi.WifiManager.LocalOnlyHotspotCallback
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                Slog.d("CAR.PROJECTION", "Local-only hotspot started");
                synchronized (CarProjectionService.this.mLock) {
                    CarProjectionService.this.mLocalOnlyHotspotReservation = reservation;
                }
                CarProjectionService.this.sendApStarted(reservation.getWifiConfiguration());
            }

            @Override // android.net.wifi.WifiManager.LocalOnlyHotspotCallback
            public void onStopped() {
                Slog.i("CAR.PROJECTION", "Local-only hotspot stopped.");
                synchronized (CarProjectionService.this.mLock) {
                    if (CarProjectionService.this.mLocalOnlyHotspotReservation != null) {
                        CarProjectionService.this.mLocalOnlyHotspotReservation.close();
                    }
                    CarProjectionService.this.mLocalOnlyHotspotReservation = null;
                }
                CarProjectionService.this.sendApStopped();
            }

            @Override // android.net.wifi.WifiManager.LocalOnlyHotspotCallback
            public void onFailed(int localonlyHostspotFailureReason) {
                int reason;
                Slog.w("CAR.PROJECTION", "Local-only hotspot failed, reason: " + localonlyHostspotFailureReason);
                synchronized (CarProjectionService.this.mLock) {
                    CarProjectionService.this.mLocalOnlyHotspotReservation = null;
                }
                if (localonlyHostspotFailureReason == 1) {
                    reason = 1;
                } else if (localonlyHostspotFailureReason == 3) {
                    reason = 3;
                } else if (localonlyHostspotFailureReason == 4) {
                    reason = 4;
                } else {
                    reason = 2;
                }
                CarProjectionService.this.sendApFailed(reason);
            }
        }, this.mHandler);
    }

    private void stopLocalOnlyApLocked() {
        Slog.i("CAR.PROJECTION", "stopLocalOnlyApLocked");
        WifiManager.LocalOnlyHotspotReservation localOnlyHotspotReservation = this.mLocalOnlyHotspotReservation;
        if (localOnlyHotspotReservation == null) {
            Slog.w("CAR.PROJECTION", "Requested to stop local-only hotspot which was already stopped.");
            return;
        }
        localOnlyHotspotReservation.close();
        this.mLocalOnlyHotspotReservation = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendApStarted(WifiConfiguration wifiConfiguration) {
        WifiConfiguration localWifiConfig = new WifiConfiguration(wifiConfiguration);
        localWifiConfig.BSSID = this.mApBssid;
        Message message = Message.obtain();
        message.what = 0;
        message.obj = localWifiConfig;
        Slog.i("CAR.PROJECTION", "Sending PROJECTION_AP_STARTED, ssid: " + localWifiConfig.getPrintableSsid() + ", apBand: " + localWifiConfig.apBand + ", apChannel: " + localWifiConfig.apChannel + ", bssid: " + localWifiConfig.BSSID);
        sendApStatusMessage(message);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendApStopped() {
        Message message = Message.obtain();
        message.what = 1;
        sendApStatusMessage(message);
        unregisterWirelessClients();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendApFailed(int reason) {
        Message message = Message.obtain();
        message.what = 2;
        message.arg1 = reason;
        sendApStatusMessage(message);
        unregisterWirelessClients();
    }

    private void sendApStatusMessage(Message message) {
        List<WirelessClient> clients;
        synchronized (this.mLock) {
            clients = new ArrayList<>(this.mWirelessClients.values());
        }
        for (WirelessClient client : clients) {
            client.send(message);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        this.mContext.registerReceiver(this.mBroadcastReceiver, new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleWifiApStateChange(int currState, int prevState, int errorCode, String ifaceName, int mode) {
        if (currState == 12 || currState == 13) {
            Slog.d("CAR.PROJECTION", "handleWifiApStateChange, curState: " + currState + ", prevState: " + prevState + ", errorCode: " + errorCode + ", ifaceName: " + ifaceName + ", mode: " + mode);
            try {
                NetworkInterface iface = NetworkInterface.getByName(ifaceName);
                byte[] bssid = iface.getHardwareAddress();
                this.mApBssid = String.format("%02x:%02x:%02x:%02x:%02x:%02x", Byte.valueOf(bssid[0]), Byte.valueOf(bssid[1]), Byte.valueOf(bssid[2]), Byte.valueOf(bssid[3]), Byte.valueOf(bssid[4]), Byte.valueOf(bssid[5]));
            } catch (SocketException e) {
                Slog.e("CAR.PROJECTION", e.toString(), e);
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        synchronized (this.mLock) {
            this.mKeyEventHandlers.clear();
        }
        this.mContext.unregisterReceiver(this.mBroadcastReceiver);
    }

    @Override // com.android.car.BinderInterfaceContainer.BinderEventHandler
    public void onBinderDeath(BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> iface) {
        unregisterKeyEventHandler(iface.binderInterface);
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("**CarProjectionService**");
        synchronized (this.mLock) {
            writer.println("Registered key event handlers:");
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> handler : this.mKeyEventHandlers.getInterfaces()) {
                ProjectionKeyEventHandler projectionKeyEventHandler = (ProjectionKeyEventHandler) handler;
                writer.print("  ");
                writer.println(projectionKeyEventHandler.toString());
            }
            writer.println("Local-only hotspot reservation: " + this.mLocalOnlyHotspotReservation);
            writer.println("Wireless clients: " + this.mWirelessClients.size());
            writer.println("Current wifi mode: " + this.mWifiMode);
            writer.println("SoftApCallback: " + this.mSoftApCallback);
            writer.println("Bound to projection app: " + this.mBound);
            writer.println("Registered Service: " + this.mRegisteredService);
            writer.println("Current projection state: " + this.mCurrentProjectionState);
            writer.println("Current projection package: " + this.mCurrentProjectionPackage);
            writer.println("Projection status: " + this.mProjectionReceiverClients);
            writer.println("Projection status listeners: " + this.mProjectionStatusListeners.getInterfaces());
            writer.println("WifiScanner: " + this.mWifiScanner);
        }
    }

    public void onKeyEvent(int keyEvent) {
        Slog.d("CAR.PROJECTION", "Dispatching key event: " + keyEvent);
        synchronized (this.mLock) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> eventHandlerInterface : this.mKeyEventHandlers.getInterfaces()) {
                ProjectionKeyEventHandler eventHandler = (ProjectionKeyEventHandler) eventHandlerInterface;
                if (eventHandler.canHandleEvent(keyEvent)) {
                    try {
                        eventHandler.binderInterface.onKeyEvent(keyEvent);
                    } catch (RemoteException e) {
                        Slog.e("CAR.PROJECTION", "Cannot dispatch event to client", e);
                    }
                }
            }
        }
    }

    @GuardedBy({"mLock"})
    private void updateInputServiceHandlerLocked() {
        BitSet newEvents = computeHandledEventsLocked();
        if (!newEvents.isEmpty()) {
            this.mCarInputService.setProjectionKeyEventHandler(this, newEvents);
        } else {
            this.mCarInputService.setProjectionKeyEventHandler(null, null);
        }
    }

    @GuardedBy({"mLock"})
    private BitSet computeHandledEventsLocked() {
        BitSet rv = new BitSet();
        for (BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> handlerInterface : this.mKeyEventHandlers.getInterfaces()) {
            rv.or(((ProjectionKeyEventHandler) handlerInterface).mHandledEvents);
        }
        return rv;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setUiMode(Integer uiMode) {
        synchronized (this.mLock) {
            this.mProjectionOptions = createProjectionOptionsBuilder().setUiMode(uiMode.intValue()).build();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setAccessPointTethering(boolean tetherEnabled) {
        synchronized (this.mLock) {
            this.mWifiMode = tetherEnabled ? 1 : 2;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ProjectionKeyEventHandlerContainer extends BinderInterfaceContainer<ICarProjectionKeyEventHandler> {
        ProjectionKeyEventHandlerContainer(CarProjectionService service) {
            super(service);
        }

        ProjectionKeyEventHandler get(ICarProjectionKeyEventHandler projectionCallback) {
            return (ProjectionKeyEventHandler) getBinderInterface(projectionCallback);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ProjectionKeyEventHandler extends BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> {
        private BitSet mHandledEvents;

        private ProjectionKeyEventHandler(ProjectionKeyEventHandlerContainer holder, ICarProjectionKeyEventHandler binder, BitSet handledEvents) {
            super(holder, binder);
            this.mHandledEvents = handledEvents;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean canHandleEvent(int event) {
            return this.mHandledEvents.get(event);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setHandledEvents(BitSet handledEvents) {
            this.mHandledEvents = handledEvents;
        }

        public String toString() {
            return "ProjectionKeyEventHandler{events=" + this.mHandledEvents + "}";
        }
    }

    private void registerWirelessClient(WirelessClient client) throws RemoteException {
        synchronized (this.mLock) {
            if (unregisterWirelessClientLocked(client.token)) {
                Slog.i("CAR.PROJECTION", "Client was already registered, override it.");
            }
            this.mWirelessClients.put(client.token, client);
        }
        client.token.linkToDeath(new WirelessClientDeathRecipient(this, client), 0);
    }

    private void unregisterWirelessClients() {
        synchronized (this.mLock) {
            for (WirelessClient client : this.mWirelessClients.values()) {
                client.token.unlinkToDeath(client.deathRecipient, 0);
            }
            this.mWirelessClients.clear();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean unregisterWirelessClientLocked(IBinder token) {
        WirelessClient client = this.mWirelessClients.remove(token);
        if (client != null) {
            token.unlinkToDeath(client.deathRecipient, 0);
        }
        return client != null;
    }

    private void ensureApConfiguration() {
        WifiConfiguration apConfig = this.mWifiManager.getWifiApConfiguration();
        if (apConfig != null && apConfig.apBand != 1 && this.mWifiManager.is5GHzBandSupported()) {
            apConfig.apBand = 1;
            this.mWifiManager.setWifiApConfiguration(apConfig);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class ProjectionSoftApCallback implements WifiManager.SoftApCallback {
        private boolean mCurrentStateCall;

        private ProjectionSoftApCallback() {
            this.mCurrentStateCall = true;
        }

        public void onStateChanged(int state, int softApFailureReason) {
            int reason;
            Slog.i("CAR.PROJECTION", "ProjectionSoftApCallback, onStateChanged, state: " + state + ", failed reason: " + softApFailureReason + ", currentStateCall: " + this.mCurrentStateCall);
            if (this.mCurrentStateCall) {
                this.mCurrentStateCall = false;
            } else if (state == 11) {
                CarProjectionService.this.sendApStopped();
            } else if (state == 13) {
                CarProjectionService carProjectionService = CarProjectionService.this;
                carProjectionService.sendApStarted(carProjectionService.mWifiManager.getWifiApConfiguration());
            } else if (state == 14) {
                Slog.w("CAR.PROJECTION", "WIFI_AP_STATE_FAILED, reason: " + softApFailureReason);
                if (softApFailureReason == 1) {
                    reason = 1;
                } else {
                    reason = 2;
                }
                CarProjectionService.this.sendApFailed(reason);
            }
        }

        public void onNumClientsChanged(int numClients) {
            Slog.i("CAR.PROJECTION", "ProjectionSoftApCallback, onNumClientsChanged: " + numClients);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class WirelessClient {
        public IBinder.DeathRecipient deathRecipient;
        public final Messenger messenger;
        public final IBinder token;

        private WirelessClient(Messenger messenger, IBinder token) {
            this.messenger = messenger;
            this.token = token;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static WirelessClient of(Messenger messenger, IBinder token) {
            return new WirelessClient(messenger, token);
        }

        void send(Message message) {
            try {
                Slog.d("CAR.PROJECTION", "Sending message " + message.what + " to " + this);
                this.messenger.send(message);
            } catch (RemoteException e) {
                Slog.e("CAR.PROJECTION", "Failed to send message", e);
            }
        }

        public String toString() {
            return getClass().getSimpleName() + "{token= " + this.token + ", deathRecipient=" + this.deathRecipient + "}";
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class WirelessClientDeathRecipient implements IBinder.DeathRecipient {
        final WirelessClient mClient;
        final WeakReference<CarProjectionService> mServiceRef;

        WirelessClientDeathRecipient(CarProjectionService service, WirelessClient client) {
            this.mServiceRef = new WeakReference<>(service);
            this.mClient = client;
            this.mClient.deathRecipient = this;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.w("CAR.PROJECTION", "Wireless client " + this.mClient + " died.");
            CarProjectionService service = this.mServiceRef.get();
            if (service == null) {
                return;
            }
            synchronized (service.mLock) {
                service.unregisterWirelessClientLocked(this.mClient.token);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ProjectionReceiverClient {
        private final IBinder.DeathRecipient mDeathRecipient;
        private ProjectionStatus mProjectionStatus;

        ProjectionReceiverClient(IBinder.DeathRecipient deathRecipient) {
            this.mDeathRecipient = deathRecipient;
        }

        public String toString() {
            return "ProjectionReceiverClient{mDeathRecipient=" + this.mDeathRecipient + ", mProjectionStatus=" + this.mProjectionStatus + '}';
        }
    }
}
