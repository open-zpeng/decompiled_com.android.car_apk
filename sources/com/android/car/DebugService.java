package com.android.car;

import android.car.encryptionrunner.DummyEncryptionRunner;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyType;
import android.os.Handler;
import android.os.Message;
import android.util.Slog;
import android.view.View;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import com.android.car.hal.VehicleHal;
import com.android.internal.os.ProcessCpuTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
/* loaded from: classes3.dex */
public class DebugService {
    public static final String TAG = "DebugService";
    Context mContext;
    private View mView;
    ArrayList<String> mEventString = new ArrayList<>();
    int mEventStringHight = 0;
    final int STRING = 1048576;
    final int BOOLEAN = 2097152;
    final int INT32 = 4194304;
    final int INT32_VEC = VehiclePropertyType.INT32_VEC;
    final int INT64 = VehiclePropertyType.INT64;
    final int INT64_VEC = VehiclePropertyType.INT64_VEC;
    final int FLOAT = VehiclePropertyType.FLOAT;
    final int FLOAT_VEC = VehiclePropertyType.FLOAT_VEC;
    final int BYTES = VehiclePropertyType.BYTES;

    public DebugService(Context c) {
        this.mContext = c;
    }

    public void init() {
        Slog.i(TAG, DummyEncryptionRunner.INIT);
        this.mView = new LoadView(this.mContext);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -2, 2015, 24, -3);
        params.gravity = 8388661;
        params.setTitle("Load Average");
        WindowManager wm = (WindowManager) this.mContext.getSystemService("window");
        wm.addView(this.mView, params);
    }

    public void release() {
        Slog.i(TAG, "release");
        ((WindowManager) this.mContext.getSystemService("window")).removeView(this.mView);
        this.mView = null;
    }

    private String dumpEvent(VehicleHal.VehiclePropertyEventInfo evnet) {
        VehiclePropValue value = evnet.getLastEvent();
        StringBuilder builder = new StringBuilder();
        builder.append("0x");
        builder.append(Integer.toHexString(value.prop));
        builder.append(":");
        int i = value.prop & VehiclePropertyType.MASK;
        if (i == 1048576) {
            builder.append(value.value.stringValue);
        } else if (i == 2097152 || i == 4194304 || i == 4259840) {
            builder.append(Arrays.toString(value.value.int32Values.toArray()));
        } else if (i == 5242880 || i == 5308416) {
            builder.append(Arrays.toString(value.value.int64Values.toArray()));
        } else if (i == 6291456 || i == 6356992) {
            builder.append(Arrays.toString(value.value.floatValues.toArray()));
        } else if (i == 7340032) {
            if (value.value.bytes.size() > 20) {
                Object[] bytes = Arrays.copyOf(value.value.bytes.toArray(), 20);
                builder.append(Arrays.toString(bytes));
            } else {
                builder.append(Arrays.toString(value.value.bytes.toArray()));
            }
        } else {
            builder.append("unknow");
        }
        return builder.toString();
    }

    public void onEvent(HashMap<Integer, VehicleHal.VehiclePropertyEventInfo> events) {
        this.mEventString.clear();
        for (VehicleHal.VehiclePropertyEventInfo info : events.values()) {
            this.mEventString.add(String.format("%d:%s", Integer.valueOf(info.getCount()), dumpEvent(info)));
        }
        this.mEventStringHight = events.size() * 10;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static final class CpuTracker extends ProcessCpuTracker {
        public final int CanvasWidth;
        public final int dataLength;
        private float[] mCpuC0Freq;
        private long mCpuC0MaxFreq;
        private float[] mCpuC1Freq;
        private long mCpuC1MaxFreq;
        String mCpuFreqText;
        int mCpuFreqWidth;
        private float[] mCpuTempData;
        String mCpuTempText;
        int mCpuTempWidth;
        private long mCurCpuC0Freq;
        private long mCurCpuC1Freq;
        private long mCurCpuTemp;
        String mLoadText;
        int mLoadWidth;
        private final Paint mPaint;

        CpuTracker(Paint paint) {
            super(false);
            this.dataLength = 100;
            this.CanvasWidth = 1600;
            this.mCpuTempData = new float[100];
            this.mCpuC0Freq = new float[100];
            this.mCpuC0MaxFreq = 1593600L;
            this.mCpuC1Freq = new float[100];
            this.mCpuC1MaxFreq = 2054400L;
            this.mPaint = paint;
            for (int i = 0; i < 100; i++) {
                if (i % 2 == 0) {
                    float[] fArr = this.mCpuC1Freq;
                    float[] fArr2 = this.mCpuC0Freq;
                    float f = i * 4.0f;
                    this.mCpuTempData[i] = f;
                    fArr2[i] = f;
                    fArr[i] = f;
                } else {
                    this.mCpuTempData[i] = 100.0f;
                    this.mCpuC0Freq[i] = 210.0f;
                    this.mCpuC1Freq[i] = 320.0f;
                }
            }
        }

        public void onLoadChanged(float load1, float load5, float load15) {
            this.mLoadText = load1 + " / " + load5 + " / " + load15;
            this.mLoadWidth = (int) this.mPaint.measureText(this.mLoadText);
        }

        public void onCpuTempChanged(long cpuTemp) {
            this.mCpuTempText = "cpu temp:" + cpuTemp;
            this.mCurCpuTemp = cpuTemp;
            this.mCpuTempWidth = 1600;
        }

        public void onCpuFreqChanged(long[] cpuFreq) {
            StringBuffer buffer = new StringBuffer("");
            for (int i = 0; i < 4; i++) {
                StringBuilder sb = new StringBuilder();
                sb.append("cpu");
                sb.append(i);
                sb.append(":");
                sb.append(cpuFreq[i] == 0 ? "offline" : Long.valueOf(cpuFreq[i]));
                sb.append(" ");
                buffer.append(sb.toString());
            }
            this.mCpuFreqText = buffer.toString();
            this.mCurCpuC0Freq = cpuFreq[0];
            this.mCurCpuC1Freq = cpuFreq[2];
            this.mCpuFreqWidth = 1600;
        }

        public int onMeasureProcessName(String name) {
            return (int) this.mPaint.measureText(name);
        }

        public void update() {
            super.update();
            int i = 0;
            while (true) {
                float[] fArr = this.mCpuTempData;
                if (i < fArr.length - 2) {
                    if (i % 2 != 0) {
                        fArr[i] = fArr[i + 2];
                        float[] fArr2 = this.mCpuC0Freq;
                        fArr2[i] = fArr2[i + 2];
                        float[] fArr3 = this.mCpuC1Freq;
                        fArr3[i] = fArr3[i + 2];
                    }
                    i++;
                } else {
                    int i2 = fArr.length;
                    fArr[i2 - 1] = 100.0f - ((float) this.mCurCpuTemp);
                    float[] fArr4 = this.mCpuC0Freq;
                    fArr4[fArr4.length - 1] = 210.0f - ((((float) this.mCurCpuC0Freq) / ((float) this.mCpuC0MaxFreq)) * 100.0f);
                    float[] fArr5 = this.mCpuC1Freq;
                    fArr5[fArr5.length - 1] = 320.0f - ((((float) this.mCurCpuC1Freq) / ((float) this.mCpuC1MaxFreq)) * 100.0f);
                    return;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class LoadView extends View {
        private Paint mAddedPaint;
        private float mAscent;
        private int mFH;
        private Handler mHandler;
        private Paint mIrqPaint;
        private Paint mLoadPaint;
        private int mNeededHeight;
        private int mNeededWidth;
        private Paint mRemovedPaint;
        private Paint mShadow2Paint;
        private Paint mShadowPaint;
        private final CpuTracker mStats;
        private Paint mSystemPaint;
        private Paint mUserPaint;

        LoadView(Context c) {
            super(c);
            int textSize;
            this.mHandler = new Handler() { // from class: com.android.car.DebugService.LoadView.1
                @Override // android.os.Handler
                public void handleMessage(Message msg) {
                    if (msg.what == 1) {
                        LoadView.this.mStats.update();
                        LoadView.this.updateDisplay();
                        Message m = obtainMessage(1);
                        sendMessageDelayed(m, 2000L);
                    }
                }
            };
            setPadding(4, 4, 4, 4);
            float density = c.getResources().getDisplayMetrics().density;
            if (density < 1.0f) {
                textSize = 9;
            } else {
                textSize = (int) (20.0f * density);
                if (textSize < 10) {
                    textSize = 10;
                }
            }
            this.mLoadPaint = new Paint();
            this.mLoadPaint.setAntiAlias(true);
            this.mLoadPaint.setTextSize(textSize);
            this.mLoadPaint.setARGB(255, 0, 255, 255);
            this.mAddedPaint = new Paint();
            this.mAddedPaint.setAntiAlias(true);
            this.mAddedPaint.setTextSize(textSize);
            this.mAddedPaint.setARGB(255, 128, 255, 128);
            this.mRemovedPaint = new Paint();
            this.mRemovedPaint.setAntiAlias(true);
            this.mRemovedPaint.setStrikeThruText(true);
            this.mRemovedPaint.setTextSize(textSize);
            this.mRemovedPaint.setARGB(255, 255, 128, 128);
            this.mShadowPaint = new Paint();
            this.mShadowPaint.setAntiAlias(true);
            this.mShadowPaint.setTextSize(textSize);
            this.mShadowPaint.setARGB(192, 0, 0, 0);
            this.mLoadPaint.setShadowLayer(4.0f, 0.0f, 0.0f, ViewCompat.MEASURED_STATE_MASK);
            this.mShadow2Paint = new Paint();
            this.mShadow2Paint.setAntiAlias(true);
            this.mShadow2Paint.setTextSize(textSize);
            this.mShadow2Paint.setARGB(192, 0, 0, 0);
            this.mLoadPaint.setShadowLayer(2.0f, 0.0f, 0.0f, ViewCompat.MEASURED_STATE_MASK);
            this.mIrqPaint = new Paint();
            this.mIrqPaint.setARGB(128, 0, 0, 255);
            this.mIrqPaint.setShadowLayer(2.0f, 0.0f, 0.0f, ViewCompat.MEASURED_STATE_MASK);
            this.mSystemPaint = new Paint();
            this.mSystemPaint.setARGB(128, 255, 0, 0);
            this.mSystemPaint.setShadowLayer(2.0f, 0.0f, 0.0f, ViewCompat.MEASURED_STATE_MASK);
            this.mUserPaint = new Paint();
            this.mUserPaint.setARGB(128, 0, 255, 0);
            this.mSystemPaint.setShadowLayer(2.0f, 0.0f, 0.0f, ViewCompat.MEASURED_STATE_MASK);
            this.mAscent = this.mLoadPaint.ascent();
            float descent = this.mLoadPaint.descent();
            this.mFH = (int) ((descent - this.mAscent) + 0.5f);
            this.mStats = new CpuTracker(this.mLoadPaint);
            this.mStats.init();
            updateDisplay();
        }

        @Override // android.view.View
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            this.mHandler.sendEmptyMessage(1);
        }

        @Override // android.view.View
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            this.mHandler.removeMessages(1);
        }

        @Override // android.view.View
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(this.mNeededWidth, widthMeasureSpec), resolveSize(this.mNeededHeight, heightMeasureSpec));
        }

        @Override // android.view.View
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int i = this.mNeededWidth;
            int width = getWidth() - 1;
            CpuTracker stats = this.mStats;
            canvas.drawText(stats.mCpuTempText, 400.0f, 0 + 100, this.mLoadPaint);
            canvas.drawLines(stats.mCpuTempData, 0, stats.mCpuTempData.length, this.mUserPaint);
            canvas.drawLines(stats.mCpuTempData, 2, stats.mCpuTempData.length - 2, this.mUserPaint);
            canvas.drawLine(0.0f, 0 + 100, 400.0f, 0 + 100, this.mLoadPaint);
            canvas.drawLine(0.0f, 0 + 100, 0.0f, 0, this.mLoadPaint);
            int gy = 0 + 110;
            canvas.drawText(stats.mCpuFreqText, 400.0f, gy + 100, this.mLoadPaint);
            canvas.drawLines(stats.mCpuC0Freq, 0, stats.mCpuC0Freq.length, this.mUserPaint);
            canvas.drawLines(stats.mCpuC0Freq, 2, stats.mCpuC0Freq.length - 2, this.mUserPaint);
            canvas.drawLine(0.0f, gy + 100, 400.0f, gy + 100, this.mLoadPaint);
            canvas.drawLine(0.0f, gy + 100, 0.0f, gy, this.mLoadPaint);
            int gy2 = gy + 110;
            canvas.drawLines(stats.mCpuC1Freq, 0, stats.mCpuC1Freq.length, this.mUserPaint);
            canvas.drawLines(stats.mCpuC1Freq, 2, stats.mCpuC1Freq.length - 2, this.mUserPaint);
            canvas.drawLine(0.0f, gy2 + 100, 400.0f, gy2 + 100, this.mLoadPaint);
            canvas.drawLine(0.0f, gy2 + 100, 0.0f, gy2, this.mLoadPaint);
            int gy3 = gy2 + 100;
            Iterator<String> it = DebugService.this.mEventString.iterator();
            while (it.hasNext()) {
                String s = it.next();
                gy3 += 20;
                canvas.drawText(s, 0.0f, gy3, this.mLoadPaint);
            }
        }

        void updateDisplay() {
            CpuTracker stats = this.mStats;
            int NW = stats.countWorkingStats();
            int maxWidth = stats.mLoadWidth;
            for (int i = 0; i < NW; i++) {
                ProcessCpuTracker.Stats st = stats.getWorkingStats(i);
                if (st.nameWidth > maxWidth) {
                    maxWidth = st.nameWidth;
                }
            }
            int i2 = this.mPaddingLeft;
            int neededWidth = i2 + this.mPaddingRight + maxWidth;
            int i3 = this.mPaddingTop + this.mPaddingBottom;
            int i4 = this.mFH;
            int neededHeight = i3 + ((NW + 1) * i4) + (i4 * 3);
            if (neededWidth != this.mNeededWidth || neededHeight != this.mNeededHeight) {
                this.mNeededWidth = Math.max(neededWidth, 1600);
                this.mNeededHeight = neededHeight + 320 + (DebugService.this.mEventStringHight * 10);
                requestLayout();
                return;
            }
            invalidate();
        }
    }
}
