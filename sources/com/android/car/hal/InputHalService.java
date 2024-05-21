package com.android.car.hal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.util.Slog;
import android.util.SparseArray;
import android.view.KeyEvent;
import com.android.car.CarLog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.LongSupplier;
/* loaded from: classes3.dex */
public class InputHalService extends HalServiceBase {
    private static final boolean DBG = false;
    public static final int DISPLAY_INSTRUMENT_CLUSTER = 1;
    public static final int DISPLAY_MAIN = 0;
    private final VehicleHal mHal;
    @GuardedBy({"this"})
    private boolean mKeyInputSupported;
    @GuardedBy({"mKeyStates"})
    private final SparseArray<KeyState> mKeyStates;
    @GuardedBy({"this"})
    private InputListener mListener;
    private final LongSupplier mUptimeSupplier;

    /* loaded from: classes3.dex */
    public interface InputListener {
        void onKeyEvent(KeyEvent keyEvent, int i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class KeyState {
        public long mLastKeyDownTimestamp;
        public int mRepeatCount;

        private KeyState() {
            this.mLastKeyDownTimestamp = -1L;
            this.mRepeatCount = 0;
        }
    }

    public InputHalService(VehicleHal hal) {
        this(hal, new LongSupplier() { // from class: com.android.car.hal.-$$Lambda$2-WG14V_pUY_jRzlz8ohJ-pqdJA
            @Override // java.util.function.LongSupplier
            public final long getAsLong() {
                return SystemClock.uptimeMillis();
            }
        });
    }

    @VisibleForTesting
    InputHalService(VehicleHal hal, LongSupplier uptimeSupplier) {
        this.mKeyInputSupported = false;
        this.mKeyStates = new SparseArray<>();
        this.mHal = hal;
        this.mUptimeSupplier = uptimeSupplier;
    }

    public void setInputListener(InputListener listener) {
        synchronized (this) {
            if (!this.mKeyInputSupported) {
                Slog.w(CarLog.TAG_INPUT, "input listener set while key input not supported");
                return;
            }
            this.mListener = listener;
            this.mHal.subscribeProperty(this, VehicleProperty.HW_KEY_INPUT);
        }
    }

    public synchronized boolean isKeyInputSupported() {
        return this.mKeyInputSupported;
    }

    @Override // com.android.car.hal.HalServiceBase
    public void init() {
    }

    @Override // com.android.car.hal.HalServiceBase
    public void release() {
        synchronized (this) {
            this.mListener = null;
            this.mKeyInputSupported = false;
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public Collection<VehiclePropConfig> takeSupportedProperties(Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> supported = new LinkedList<>();
        for (VehiclePropConfig p : allProperties) {
            if (p.prop == 289475088) {
                supported.add(p);
                synchronized (this) {
                    this.mKeyInputSupported = true;
                }
            }
        }
        return supported;
    }

    @Override // com.android.car.hal.HalServiceBase
    public void handleHalEvents(List<VehiclePropValue> values) {
        InputListener listener;
        synchronized (this) {
            listener = this.mListener;
        }
        if (listener == null) {
            Slog.w(CarLog.TAG_INPUT, "Input event while listener is null");
            return;
        }
        for (VehiclePropValue v : values) {
            if (v.prop != 289475088) {
                Slog.e(CarLog.TAG_INPUT, "Wrong event dispatched, prop:0x" + Integer.toHexString(v.prop));
            } else {
                int action = v.value.int32Values.get(0).intValue() != 0 ? 1 : 0;
                int code = v.value.int32Values.get(1).intValue();
                int display = v.value.int32Values.get(2).intValue();
                int indentsCount = v.value.int32Values.size() >= 4 ? v.value.int32Values.get(3).intValue() : 1;
                while (indentsCount > 0) {
                    indentsCount--;
                    dispatchKeyEvent(listener, action, code, display);
                }
            }
        }
    }

    private void dispatchKeyEvent(InputListener listener, int action, int code, int display) {
        long downTime;
        long downTime2;
        int repeat;
        long eventTime = this.mUptimeSupplier.getAsLong();
        synchronized (this.mKeyStates) {
            try {
                KeyState state = this.mKeyStates.get(code);
                if (state == null) {
                    try {
                        state = new KeyState();
                        this.mKeyStates.put(code, state);
                    } catch (Throwable th) {
                        th = th;
                        while (true) {
                            try {
                                break;
                            } catch (Throwable th2) {
                                th = th2;
                            }
                        }
                        throw th;
                    }
                }
                if (action == 0) {
                    int repeat2 = state.mRepeatCount;
                    state.mRepeatCount = repeat2 + 1;
                    state.mLastKeyDownTimestamp = eventTime;
                    downTime2 = eventTime;
                    repeat = repeat2;
                } else {
                    long downTime3 = state.mLastKeyDownTimestamp;
                    if (downTime3 == -1) {
                        downTime = eventTime;
                    } else {
                        downTime = state.mLastKeyDownTimestamp;
                    }
                    state.mRepeatCount = 0;
                    downTime2 = downTime;
                    repeat = 0;
                }
                KeyEvent event = KeyEvent.obtain(downTime2, eventTime, action, code, repeat, 0, 0, 0, 0, 1, null);
                listener.onKeyEvent(event, display);
                event.recycle();
            } catch (Throwable th3) {
                th = th3;
            }
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*Input HAL*");
        writer.println("mKeyInputSupported:" + this.mKeyInputSupported);
    }
}
