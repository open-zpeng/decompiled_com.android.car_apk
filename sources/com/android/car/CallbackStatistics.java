package com.android.car;

import android.car.XpDebugLog;
import android.car.hardware.property.CarPropertyEvent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Slog;
import com.android.car.CallbackStatistics;
import com.android.settingslib.accessibility.AccessibilityUtils;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
/* loaded from: classes3.dex */
public class CallbackStatistics {
    public static final Duration MONITOR_PROCESS_CALL_BACK_DELAY_15_MIN = Duration.ofMinutes(15);
    public static final String TAG = "-Statistics";
    private final Handler mHandler;
    private final String mTag;
    private final Map<ProcessInfo, Map<Integer, AtomicLong>> mClientPropMethodCallCountMap = new ConcurrentHashMap();
    private volatile long mPeriod = 0;
    private final int mMyPid = Process.myPid();

    public CallbackStatistics(String tag, boolean cyclicCheckCallCount) {
        this.mTag = tag + TAG;
        if (!Build.IS_USER) {
            if (cyclicCheckCallCount) {
                this.mHandler = SingletonMethodCallCountMonitor.getSingleton().getHandler();
                cyclicCheckClientsCallback(MONITOR_PROCESS_CALL_BACK_DELAY_15_MIN.toMillis(), MONITOR_PROCESS_CALL_BACK_DELAY_15_MIN.toMillis());
                return;
            }
            this.mHandler = null;
            return;
        }
        this.mHandler = null;
    }

    private static String covertPropMethodCallCountMapToString(Map<Integer, AtomicLong> map) {
        Iterator<Map.Entry<Integer, AtomicLong>> i = map.entrySet().iterator();
        if (!i.hasNext()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        while (true) {
            Map.Entry<Integer, AtomicLong> e = i.next();
            int key = e.getKey().intValue();
            long value = e.getValue().get();
            sb.append(XpDebugLog.getPropertyDescription(key));
            sb.append(AccessibilityUtils.ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR);
            sb.append(value);
            if (!i.hasNext()) {
                sb.append('}');
                return sb.toString();
            }
            sb.append(',');
            sb.append(' ');
        }
    }

    public int getMyPid() {
        return this.mMyPid;
    }

    public void dump(PrintWriter writer) {
        if (!Build.IS_USER) {
            writer.println("    mClientCallbackMap: " + this.mClientPropMethodCallCountMap.size());
            for (Map.Entry<ProcessInfo, Map<Integer, AtomicLong>> entry : this.mClientPropMethodCallCountMap.entrySet()) {
                writer.println("        " + entry.getKey());
                for (Map.Entry<Integer, AtomicLong> innerEntry : entry.getValue().entrySet()) {
                    writer.println("            " + XpDebugLog.getPropertyDescription(innerEntry.getKey().intValue()) + ", callback count = " + innerEntry.getValue().longValue());
                }
            }
        }
    }

    public void addPropMethodCallCount(int propId, String processName, int pid, IBinder listenerBinder) {
        if (!Build.IS_USER && pid != this.mMyPid) {
            ProcessInfo processInfo = new ProcessInfo(processName, pid, listenerBinder);
            try {
                Map<Integer, AtomicLong> stringAtomicLongMap = this.mClientPropMethodCallCountMap.computeIfAbsent(processInfo, new Function() { // from class: com.android.car.-$$Lambda$CallbackStatistics$oSY8BxjYOeW6BJQ1ktJ1rfqcdic
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        return CallbackStatistics.lambda$addPropMethodCallCount$0((CallbackStatistics.ProcessInfo) obj);
                    }
                });
                long value = stringAtomicLongMap.computeIfAbsent(Integer.valueOf(propId), new Function() { // from class: com.android.car.-$$Lambda$CallbackStatistics$gxnPfI1gnY55mWVfdcBzD4bJuyA
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        return CallbackStatistics.lambda$addPropMethodCallCount$1((Integer) obj);
                    }
                }).incrementAndGet();
                if (value < 0) {
                    stringAtomicLongMap.replace(Integer.valueOf(propId), new AtomicLong(1L));
                    String str = this.mTag;
                    Slog.i(str, processInfo + ", callback prop: " + XpDebugLog.getPropertyDescription(propId) + ", new count = 1");
                } else if (CarLog.isCallbackLogEnable(propId)) {
                    String str2 = this.mTag;
                    Slog.i(str2, processInfo + ", callback prop: " + XpDebugLog.getPropertyDescription(propId) + ", count = " + value);
                }
            } catch (Exception ex) {
                Slog.e(this.mTag, "calculateCallbackCount failed: ", ex);
            }
        }
    }

    public static /* synthetic */ Map lambda$addPropMethodCallCount$0(ProcessInfo p) {
        return new ConcurrentHashMap();
    }

    public static /* synthetic */ AtomicLong lambda$addPropMethodCallCount$1(Integer p) {
        return new AtomicLong();
    }

    public void addPropsMethodCallCount(List<CarPropertyEvent> propertyEvents, String processName, int pid, IBinder listenerBinder) {
        if (propertyEvents != null && !Build.IS_USER && pid != this.mMyPid) {
            final ProcessInfo processInfo = new ProcessInfo(processName, pid, listenerBinder);
            try {
                propertyEvents.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CallbackStatistics$uWg5E4bcGNVuF92oDB6Cz4W5GnA
                    @Override // java.util.function.Consumer
                    public final void accept(Object obj) {
                        CallbackStatistics.this.lambda$addPropsMethodCallCount$4$CallbackStatistics(processInfo, (CarPropertyEvent) obj);
                    }
                });
            } catch (Exception ex) {
                Slog.e(this.mTag, "calculateCallbackCount failed: ", ex);
            }
        }
    }

    public /* synthetic */ void lambda$addPropsMethodCallCount$4$CallbackStatistics(ProcessInfo processInfo, CarPropertyEvent v) {
        if (v.getEventType() == 0) {
            int propId = v.getCarPropertyValue().getPropertyId();
            Map<Integer, AtomicLong> stringAtomicLongMap = this.mClientPropMethodCallCountMap.computeIfAbsent(processInfo, new Function() { // from class: com.android.car.-$$Lambda$CallbackStatistics$kQMnkL7dDQFfW5_FrRppxcCUZew
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return CallbackStatistics.lambda$addPropsMethodCallCount$2((CallbackStatistics.ProcessInfo) obj);
                }
            });
            long value = stringAtomicLongMap.computeIfAbsent(Integer.valueOf(propId), new Function() { // from class: com.android.car.-$$Lambda$CallbackStatistics$rrAvMLi7fHuN2McLmFNXsWierTw
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return CallbackStatistics.lambda$addPropsMethodCallCount$3((Integer) obj);
                }
            }).incrementAndGet();
            if (value < 0) {
                stringAtomicLongMap.replace(Integer.valueOf(propId), new AtomicLong(1L));
                String str = this.mTag;
                Slog.i(str, processInfo + ", callback prop: " + XpDebugLog.getPropertyDescription(propId) + ", new count = 1");
            } else if (CarLog.isCallbackLogEnable(propId)) {
                String str2 = this.mTag;
                Slog.i(str2, processInfo + ", callback prop: " + XpDebugLog.getPropertyDescription(propId) + ", count = " + value);
            }
        }
    }

    public static /* synthetic */ Map lambda$addPropsMethodCallCount$2(ProcessInfo p) {
        return new ConcurrentHashMap();
    }

    public static /* synthetic */ AtomicLong lambda$addPropsMethodCallCount$3(Integer p) {
        return new AtomicLong();
    }

    public void removeProcess(String processName, int pid, IBinder listenerBinder) {
    }

    private void cyclicCheckClientsCallback(long delayMillis, long periodMillis) {
        if (!Build.IS_USER && delayMillis > 0) {
            synchronized (this) {
                this.mPeriod = periodMillis;
            }
            Handler handler = this.mHandler;
            if (handler != null && delayMillis > 0) {
                handler.postDelayed(new $$Lambda$CallbackStatistics$NRe21MeNizKCbPWTlBD1rvjmgMQ(this), delayMillis);
            }
        }
    }

    public void checkClientsCallback() {
        try {
            if (!this.mClientPropMethodCallCountMap.isEmpty()) {
                long totalCallback = ((Long) this.mClientPropMethodCallCountMap.values().stream().flatMap(new Function() { // from class: com.android.car.-$$Lambda$CallbackStatistics$sk10SoC1gKIV8UtsNT7rGyaJ4P0
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        Stream stream;
                        stream = ((Map) obj).values().stream();
                        return stream;
                    }
                }).map(new Function() { // from class: com.android.car.-$$Lambda$BD_axBP7_ZmJkVDm9udBYEo_14w
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        return Long.valueOf(((AtomicLong) obj).get());
                    }
                }).reduce(0L, new BinaryOperator() { // from class: com.android.car.-$$Lambda$R8aE88Z140TFfTli76Hdc3YzhU4
                    @Override // java.util.function.BiFunction
                    public final Object apply(Object obj, Object obj2) {
                        return Long.valueOf(Long.sum(((Long) obj).longValue(), ((Long) obj2).longValue()));
                    }
                })).longValue();
                String str = this.mTag;
                Slog.i(str, "Total callback count = " + totalCallback);
                this.mClientPropMethodCallCountMap.entrySet().forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CallbackStatistics$F_srxxNumA_jsfBMfcGd3W49cXM
                    @Override // java.util.function.Consumer
                    public final void accept(Object obj) {
                        CallbackStatistics.this.lambda$checkClientsCallback$6$CallbackStatistics((Map.Entry) obj);
                    }
                });
            }
        } catch (Exception e) {
        }
        if (this.mPeriod > 0) {
            this.mHandler.postDelayed(new $$Lambda$CallbackStatistics$NRe21MeNizKCbPWTlBD1rvjmgMQ(this), this.mPeriod);
        }
    }

    public /* synthetic */ void lambda$checkClientsCallback$6$CallbackStatistics(Map.Entry s) {
        String str = this.mTag;
        Slog.i(str, "callback: " + s.getKey() + ": " + covertPropMethodCallCountMapToString((Map) s.getValue()));
    }

    public void release() {
        SingletonMethodCallCountMonitor.getSingleton().stop();
        this.mClientPropMethodCallCountMap.clear();
    }

    /* loaded from: classes3.dex */
    public static class ProcessInfo {
        private final IBinder mListener;
        private final int mPid;
        private final String mProcessName;

        public ProcessInfo(String name, int pid, IBinder binder) {
            this.mProcessName = name;
            this.mPid = pid;
            this.mListener = binder;
        }

        public String toString() {
            return "Process " + this.mProcessName + "(" + this.mPid + ", " + this.mListener + ")";
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof ProcessInfo) {
                ProcessInfo that = (ProcessInfo) o;
                return Objects.equals(this.mProcessName, that.mProcessName) && Objects.equals(Integer.valueOf(this.mPid), Integer.valueOf(that.mPid)) && Objects.equals(this.mListener, that.mListener);
            }
            return false;
        }

        public int hashCode() {
            return Objects.hash(this.mProcessName, Integer.valueOf(this.mPid), this.mListener);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class SingletonMethodCallCountMonitor {
        private static final String TAG = SingletonMethodCallCountMonitor.class.getSimpleName();
        private final Handler mHandler;
        private final HandlerThread mHandlerThread;
        private boolean mStopped;

        private SingletonMethodCallCountMonitor() {
            this.mHandlerThread = new HandlerThread("Monitor callback");
            this.mHandlerThread.start();
            this.mHandler = new Handler(this.mHandlerThread.getLooper());
            this.mStopped = false;
        }

        public static SingletonMethodCallCountMonitor getSingleton() {
            return Holder.SINGLETON;
        }

        public Handler getHandler() {
            return this.mHandler;
        }

        public synchronized void stop() {
            if (!this.mStopped) {
                this.mHandler.removeCallbacksAndMessages(null);
                this.mHandlerThread.quitSafely();
                try {
                    this.mHandlerThread.join(1000L);
                } catch (InterruptedException e) {
                    Slog.e(TAG, "Timeout while waiting for handler thread to join.");
                }
                this.mStopped = true;
            }
        }

        /* loaded from: classes3.dex */
        public static class Holder {
            private static final SingletonMethodCallCountMonitor SINGLETON = new SingletonMethodCallCountMonitor();

            private Holder() {
            }
        }
    }
}
