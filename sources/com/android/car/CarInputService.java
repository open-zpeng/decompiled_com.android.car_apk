package com.android.car;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.car.CarProjectionManager;
import android.car.input.CarInputHandlingService;
import android.car.input.ICarInputListener;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.CallLog;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Slog;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import com.android.car.CarInputService;
import com.android.car.hal.InputHalService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
/* loaded from: classes3.dex */
public class CarInputService implements CarServiceBase, InputHalService.InputListener {
    private static final boolean DBG = false;
    @VisibleForTesting
    static final String EXTRA_CAR_PUSH_TO_TALK = "com.android.car.input.EXTRA_CAR_PUSH_TO_TALK";
    private final AssistUtils mAssistUtils;
    private final BluetoothAdapter mBluetoothAdapter;
    @GuardedBy({"mBluetoothProfileServiceListener"})
    private BluetoothHeadsetClient mBluetoothHeadsetClient;
    private final BluetoothProfile.ServiceListener mBluetoothProfileServiceListener;
    private final KeyPressTimer mCallKeyTimer;
    private final Binder mCallback;
    @GuardedBy({"this"})
    @VisibleForTesting
    ICarInputListener mCarInputListener;
    @GuardedBy({"this"})
    private boolean mCarInputListenerBound;
    private final Context mContext;
    private final ComponentName mCustomInputServiceComponent;
    @GuardedBy({"this"})
    private final SetMultimap<Integer, Integer> mHandledKeys;
    private final InputHalService mInputHalService;
    private final ServiceConnection mInputServiceConnection;
    @GuardedBy({"this"})
    private KeyEventListener mInstrumentClusterKeyListener;
    private final Supplier<String> mLastCalledNumberSupplier;
    private final IntSupplier mLongPressDelaySupplier;
    private final KeyEventListener mMainDisplayHandler;
    @GuardedBy({"this"})
    private CarProjectionManager.ProjectionKeyEventHandler mProjectionKeyEventHandler;
    @GuardedBy({"this"})
    private final BitSet mProjectionKeyEventsSubscribed;
    private final IVoiceInteractionSessionShowCallback mShowCallback;
    private final TelecomManager mTelecomManager;
    private final KeyPressTimer mVoiceKeyTimer;

    /* loaded from: classes3.dex */
    public interface KeyEventListener {
        void onKeyEvent(KeyEvent keyEvent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static final class KeyPressTimer {
        private final Handler mHandler;
        private final IntSupplier mLongPressDelaySupplier;
        private final Runnable mLongPressRunnable;
        private final Runnable mCallback = new Runnable() { // from class: com.android.car.-$$Lambda$CarInputService$KeyPressTimer$RxS3zZd0nzAQkCrwfSaxcRP5l2k
            @Override // java.lang.Runnable
            public final void run() {
                CarInputService.KeyPressTimer.this.onTimerExpired();
            }
        };
        @GuardedBy({"this"})
        private boolean mDown = false;
        @GuardedBy({"this"})
        private boolean mLongPress = false;

        KeyPressTimer(Handler handler, IntSupplier longPressDelaySupplier, Runnable longPressRunnable) {
            this.mHandler = handler;
            this.mLongPressRunnable = longPressRunnable;
            this.mLongPressDelaySupplier = longPressDelaySupplier;
        }

        synchronized void keyDown() {
            this.mDown = true;
            this.mLongPress = false;
            this.mHandler.removeCallbacks(this.mCallback);
            this.mHandler.postDelayed(this.mCallback, this.mLongPressDelaySupplier.getAsInt());
        }

        synchronized boolean keyUp() {
            this.mHandler.removeCallbacks(this.mCallback);
            this.mDown = false;
            return this.mLongPress;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void onTimerExpired() {
            synchronized (this) {
                if (this.mDown) {
                    this.mLongPress = true;
                    this.mLongPressRunnable.run();
                }
            }
        }
    }

    private static ComponentName getDefaultInputComponent(Context context) {
        String carInputService = context.getString(R.string.inputService);
        if (TextUtils.isEmpty(carInputService)) {
            return null;
        }
        return ComponentName.unflattenFromString(carInputService);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int getViewLongPressDelay(ContentResolver cr) {
        return Settings.Secure.getIntForUser(cr, "long_press_timeout", ViewConfiguration.getLongPressTimeout(), -2);
    }

    public CarInputService(final Context context, InputHalService inputHalService) {
        this(context, inputHalService, new Handler(Looper.getMainLooper()), (TelecomManager) context.getSystemService(TelecomManager.class), new AssistUtils(context), new KeyEventListener() { // from class: com.android.car.-$$Lambda$CarInputService$7kFnLSFUa9N-3WUMKY8R0WRdzr0
            @Override // com.android.car.CarInputService.KeyEventListener
            public final void onKeyEvent(KeyEvent keyEvent) {
                ((InputManager) context.getSystemService(InputManager.class)).injectInputEvent(keyEvent, 0);
            }
        }, new Supplier() { // from class: com.android.car.-$$Lambda$CarInputService$mhL8qJm8oGLfW887YodbxCIfG6E
            @Override // java.util.function.Supplier
            public final Object get() {
                String lastOutgoingCall;
                lastOutgoingCall = CallLog.Calls.getLastOutgoingCall(context);
                return lastOutgoingCall;
            }
        }, getDefaultInputComponent(context), new IntSupplier() { // from class: com.android.car.-$$Lambda$CarInputService$Z755B13oiPRdxXHhRzBfKCaWYRc
            @Override // java.util.function.IntSupplier
            public final int getAsInt() {
                int viewLongPressDelay;
                viewLongPressDelay = CarInputService.getViewLongPressDelay(context.getContentResolver());
                return viewLongPressDelay;
            }
        });
    }

    @VisibleForTesting
    CarInputService(Context context, InputHalService inputHalService, Handler handler, TelecomManager telecomManager, AssistUtils assistUtils, KeyEventListener mainDisplayHandler, Supplier<String> lastCalledNumberSupplier, ComponentName customInputServiceComponent, IntSupplier longPressDelaySupplier) {
        this.mShowCallback = new IVoiceInteractionSessionShowCallback.Stub() { // from class: com.android.car.CarInputService.1
            public void onFailed() {
                Slog.w(CarLog.TAG_INPUT, "Failed to show VoiceInteractionSession");
            }

            public void onShown() {
            }
        };
        this.mProjectionKeyEventsSubscribed = new BitSet();
        this.mCarInputListenerBound = false;
        this.mHandledKeys = new SetMultimap<>();
        this.mCallback = new Binder() { // from class: com.android.car.CarInputService.2
            @Override // android.os.Binder
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != 1) {
                    return false;
                }
                data.setDataPosition(0);
                CarInputHandlingService.InputFilter[] handledKeys = (CarInputHandlingService.InputFilter[]) data.createTypedArray(CarInputHandlingService.InputFilter.CREATOR);
                if (handledKeys != null) {
                    CarInputService.this.setHandledKeys(handledKeys);
                }
                return true;
            }
        };
        this.mInputServiceConnection = new ServiceConnection() { // from class: com.android.car.CarInputService.3
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName name, IBinder binder) {
                synchronized (CarInputService.this) {
                    CarInputService.this.mCarInputListener = ICarInputListener.Stub.asInterface(binder);
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName name) {
                Slog.d(CarLog.TAG_INPUT, "onServiceDisconnected, name: " + name);
                synchronized (CarInputService.this) {
                    CarInputService.this.mCarInputListener = null;
                }
            }
        };
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mBluetoothProfileServiceListener = new BluetoothProfile.ServiceListener() { // from class: com.android.car.CarInputService.4
            @Override // android.bluetooth.BluetoothProfile.ServiceListener
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == 16) {
                    Slog.d(CarLog.TAG_INPUT, "Bluetooth proxy connected for HEADSET_CLIENT profile");
                    synchronized (this) {
                        CarInputService.this.mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                    }
                }
            }

            @Override // android.bluetooth.BluetoothProfile.ServiceListener
            public void onServiceDisconnected(int profile) {
                if (profile == 16) {
                    Slog.d(CarLog.TAG_INPUT, "Bluetooth proxy disconnected for HEADSET_CLIENT profile");
                    synchronized (this) {
                        CarInputService.this.mBluetoothHeadsetClient = null;
                    }
                }
            }
        };
        this.mContext = context;
        this.mInputHalService = inputHalService;
        this.mTelecomManager = telecomManager;
        this.mAssistUtils = assistUtils;
        this.mMainDisplayHandler = mainDisplayHandler;
        this.mLastCalledNumberSupplier = lastCalledNumberSupplier;
        this.mCustomInputServiceComponent = customInputServiceComponent;
        this.mLongPressDelaySupplier = longPressDelaySupplier;
        this.mVoiceKeyTimer = new KeyPressTimer(handler, longPressDelaySupplier, new Runnable() { // from class: com.android.car.-$$Lambda$CarInputService$L9JhV3ODz0L9p2ms-aiF7bgzD6c
            @Override // java.lang.Runnable
            public final void run() {
                CarInputService.this.handleVoiceAssistLongPress();
            }
        });
        this.mCallKeyTimer = new KeyPressTimer(handler, longPressDelaySupplier, new Runnable() { // from class: com.android.car.-$$Lambda$CarInputService$o3-C1DSyq366z7712pSu54NAEg8
            @Override // java.lang.Runnable
            public final void run() {
                CarInputService.this.handleCallLongPress();
            }
        });
    }

    @VisibleForTesting
    synchronized void setHandledKeys(CarInputHandlingService.InputFilter[] handledKeys) {
        this.mHandledKeys.clear();
        for (CarInputHandlingService.InputFilter handledKey : handledKeys) {
            this.mHandledKeys.put(Integer.valueOf(handledKey.mTargetDisplay), Integer.valueOf(handledKey.mKeyCode));
        }
    }

    public void setProjectionKeyEventHandler(CarProjectionManager.ProjectionKeyEventHandler listener, BitSet events) {
        synchronized (this) {
            this.mProjectionKeyEventHandler = listener;
            this.mProjectionKeyEventsSubscribed.clear();
            if (events != null) {
                this.mProjectionKeyEventsSubscribed.or(events);
            }
        }
    }

    public void setInstrumentClusterKeyListener(KeyEventListener listener) {
        synchronized (this) {
            this.mInstrumentClusterKeyListener = listener;
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        if (!this.mInputHalService.isKeyInputSupported()) {
            Slog.w(CarLog.TAG_INPUT, "Hal does not support key input.");
            return;
        }
        this.mInputHalService.setInputListener(this);
        synchronized (this) {
            this.mCarInputListenerBound = bindCarInputService();
        }
        BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
        if (bluetoothAdapter != null) {
            bluetoothAdapter.getProfileProxy(this.mContext, this.mBluetoothProfileServiceListener, 16);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        synchronized (this) {
            this.mProjectionKeyEventHandler = null;
            this.mProjectionKeyEventsSubscribed.clear();
            this.mInstrumentClusterKeyListener = null;
            if (this.mCarInputListenerBound) {
                this.mContext.unbindService(this.mInputServiceConnection);
                this.mCarInputListenerBound = false;
            }
        }
        synchronized (this.mBluetoothProfileServiceListener) {
            if (this.mBluetoothHeadsetClient != null) {
                this.mBluetoothAdapter.closeProfileProxy(16, this.mBluetoothHeadsetClient);
                this.mBluetoothHeadsetClient = null;
            }
        }
    }

    @Override // com.android.car.hal.InputHalService.InputListener
    public void onKeyEvent(KeyEvent event, int targetDisplay) {
        ICarInputListener carInputListener;
        synchronized (this) {
            carInputListener = this.mCarInputListener;
        }
        if (carInputListener != null && isCustomEventHandler(event, targetDisplay)) {
            try {
                carInputListener.onKeyEvent(event, targetDisplay);
                return;
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_INPUT, "Error while calling car input service", e);
                return;
            }
        }
        int keyCode = event.getKeyCode();
        if (keyCode == 5) {
            handleCallKey(event);
        } else if (keyCode == 231) {
            handleVoiceAssistKey(event);
        } else if (targetDisplay == 1) {
            handleInstrumentClusterKey(event);
        } else {
            this.mMainDisplayHandler.onKeyEvent(event);
        }
    }

    private synchronized boolean isCustomEventHandler(KeyEvent event, int targetDisplay) {
        return this.mHandledKeys.containsEntry(Integer.valueOf(targetDisplay), Integer.valueOf(event.getKeyCode()));
    }

    private void handleVoiceAssistKey(KeyEvent event) {
        int action = event.getAction();
        if (action == 0 && event.getRepeatCount() == 0) {
            this.mVoiceKeyTimer.keyDown();
            dispatchProjectionKeyEvent(0);
        } else if (action == 1) {
            if (this.mVoiceKeyTimer.keyUp()) {
                dispatchProjectionKeyEvent(3);
            } else if (dispatchProjectionKeyEvent(1)) {
            } else {
                launchDefaultVoiceAssistantHandler();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleVoiceAssistLongPress() {
        if (dispatchProjectionKeyEvent(2) || launchBluetoothVoiceRecognition()) {
            return;
        }
        launchDefaultVoiceAssistantHandler();
    }

    private void handleCallKey(KeyEvent event) {
        int action = event.getAction();
        if (action == 0 && event.getRepeatCount() == 0) {
            this.mCallKeyTimer.keyDown();
            dispatchProjectionKeyEvent(4);
        } else if (action == 1) {
            if (this.mCallKeyTimer.keyUp()) {
                dispatchProjectionKeyEvent(7);
            } else if (acceptCallIfRinging() || dispatchProjectionKeyEvent(5)) {
            } else {
                launchDialerHandler();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleCallLongPress() {
        if (acceptCallIfRinging() || dispatchProjectionKeyEvent(6)) {
            return;
        }
        dialLastCallHandler();
    }

    private boolean dispatchProjectionKeyEvent(int event) {
        synchronized (this) {
            CarProjectionManager.ProjectionKeyEventHandler projectionKeyEventHandler = this.mProjectionKeyEventHandler;
            if (projectionKeyEventHandler != null && this.mProjectionKeyEventsSubscribed.get(event)) {
                projectionKeyEventHandler.onKeyEvent(event);
                return true;
            }
            return false;
        }
    }

    private void launchDialerHandler() {
        Slog.i(CarLog.TAG_INPUT, "call key, launch dialer intent");
        Intent dialerIntent = new Intent("android.intent.action.DIAL");
        this.mContext.startActivityAsUser(dialerIntent, null, UserHandle.CURRENT_OR_SELF);
    }

    private void dialLastCallHandler() {
        Slog.i(CarLog.TAG_INPUT, "call key, dialing last call");
        String lastNumber = this.mLastCalledNumberSupplier.get();
        if (!TextUtils.isEmpty(lastNumber)) {
            Intent callLastNumberIntent = new Intent("android.intent.action.CALL").setData(Uri.fromParts("tel", lastNumber, null)).setFlags(268435456);
            this.mContext.startActivityAsUser(callLastNumberIntent, null, UserHandle.CURRENT_OR_SELF);
        }
    }

    private boolean acceptCallIfRinging() {
        TelecomManager telecomManager = this.mTelecomManager;
        if (telecomManager != null && telecomManager.isRinging()) {
            Slog.i(CarLog.TAG_INPUT, "call key while ringing. Answer the call!");
            this.mTelecomManager.acceptRingingCall();
            return true;
        }
        return false;
    }

    private boolean isBluetoothVoiceRecognitionEnabled() {
        Resources res = this.mContext.getResources();
        return res.getBoolean(R.bool.enableLongPressBluetoothVoiceRecognition);
    }

    private boolean launchBluetoothVoiceRecognition() {
        synchronized (this.mBluetoothProfileServiceListener) {
            if (this.mBluetoothHeadsetClient != null && isBluetoothVoiceRecognitionEnabled()) {
                List<BluetoothDevice> devices = this.mBluetoothHeadsetClient.getConnectedDevices();
                if (devices != null) {
                    for (BluetoothDevice device : devices) {
                        Bundle bundle = this.mBluetoothHeadsetClient.getCurrentAgFeatures(device);
                        if (bundle != null && bundle.getBoolean("android.bluetooth.headsetclient.extra.EXTRA_AG_FEATURE_VOICE_RECOGNITION") && this.mBluetoothHeadsetClient.startVoiceRecognition(device)) {
                            Slog.d(CarLog.TAG_INPUT, "started voice recognition on BT device at " + device.getAddress());
                            return true;
                        }
                    }
                }
                return false;
            }
            return false;
        }
    }

    private void launchDefaultVoiceAssistantHandler() {
        Slog.i(CarLog.TAG_INPUT, "voice key, invoke AssistUtils");
        if (this.mAssistUtils.getAssistComponentForUser(ActivityManager.getCurrentUser()) == null) {
            Slog.w(CarLog.TAG_INPUT, "Unable to retrieve assist component for current user");
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean(EXTRA_CAR_PUSH_TO_TALK, true);
        this.mAssistUtils.showSessionForActiveService(args, 32, this.mShowCallback, (IBinder) null);
    }

    private void handleInstrumentClusterKey(KeyEvent event) {
        KeyEventListener listener;
        synchronized (this) {
            listener = this.mInstrumentClusterKeyListener;
        }
        if (listener == null) {
            return;
        }
        listener.onKeyEvent(event);
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void dump(PrintWriter writer) {
        writer.println("*Input Service*");
        writer.println("mCustomInputServiceComponent: " + this.mCustomInputServiceComponent);
        writer.println("mCarInputListenerBound: " + this.mCarInputListenerBound);
        writer.println("mCarInputListener: " + this.mCarInputListener);
        writer.println("Long-press delay: " + this.mLongPressDelaySupplier.getAsInt() + "ms");
    }

    private boolean bindCarInputService() {
        if (this.mCustomInputServiceComponent == null) {
            Slog.i(CarLog.TAG_INPUT, "Custom input service was not configured");
            return false;
        }
        Slog.d(CarLog.TAG_INPUT, "bindCarInputService, component: " + this.mCustomInputServiceComponent);
        Intent intent = new Intent();
        Bundle extras = new Bundle();
        extras.putBinder("callback_binder", this.mCallback);
        intent.putExtras(extras);
        intent.setComponent(this.mCustomInputServiceComponent);
        return this.mContext.bindService(intent, this.mInputServiceConnection, 1);
    }
}
