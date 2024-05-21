package com.android.settingslib.notification;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.provider.Settings;
import android.service.notification.Condition;
import android.service.notification.ZenModeConfig;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.policy.PhoneWindow;
import com.android.settingslib.R;
import java.util.Arrays;
/* loaded from: classes3.dex */
public class ZenDurationDialog {
    @VisibleForTesting
    protected static final int ALWAYS_ASK_CONDITION_INDEX = 2;
    @VisibleForTesting
    protected static final int COUNTDOWN_CONDITION_INDEX = 1;
    private static final int DEFAULT_BUCKET_INDEX;
    @VisibleForTesting
    protected static final int FOREVER_CONDITION_INDEX = 0;
    @VisibleForTesting
    protected static final int MAX_BUCKET_MINUTES;
    private static final int[] MINUTE_BUCKETS = ZenModeConfig.MINUTE_BUCKETS;
    @VisibleForTesting
    protected static final int MIN_BUCKET_MINUTES;
    @VisibleForTesting
    protected Context mContext;
    @VisibleForTesting
    protected LayoutInflater mLayoutInflater;
    private RadioGroup mZenRadioGroup;
    @VisibleForTesting
    protected LinearLayout mZenRadioGroupContent;
    @VisibleForTesting
    protected int mBucketIndex = -1;
    private int MAX_MANUAL_DND_OPTIONS = 3;

    static {
        int[] iArr = MINUTE_BUCKETS;
        MIN_BUCKET_MINUTES = iArr[0];
        MAX_BUCKET_MINUTES = iArr[iArr.length - 1];
        DEFAULT_BUCKET_INDEX = Arrays.binarySearch(iArr, 60);
    }

    public ZenDurationDialog(Context context) {
        this.mContext = context;
    }

    public Dialog createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mContext);
        setupDialog(builder);
        return builder.create();
    }

    public void setupDialog(AlertDialog.Builder builder) {
        final int zenDuration = Settings.Secure.getInt(this.mContext.getContentResolver(), "zen_duration", 0);
        builder.setTitle(R.string.zen_mode_duration_settings_title).setNegativeButton(R.string.cancel, (DialogInterface.OnClickListener) null).setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() { // from class: com.android.settingslib.notification.ZenDurationDialog.1
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                ZenDurationDialog.this.updateZenDuration(zenDuration);
            }
        });
        View contentView = getContentView();
        setupRadioButtons(zenDuration);
        builder.setView(contentView);
    }

    @VisibleForTesting
    protected void updateZenDuration(int currZenDuration) {
        int checkedRadioButtonId = this.mZenRadioGroup.getCheckedRadioButtonId();
        int newZenDuration = Settings.Secure.getInt(this.mContext.getContentResolver(), "zen_duration", 0);
        if (checkedRadioButtonId == 0) {
            newZenDuration = 0;
            MetricsLogger.action(this.mContext, 1343);
        } else if (checkedRadioButtonId == 1) {
            ConditionTag tag = getConditionTagAt(checkedRadioButtonId);
            newZenDuration = tag.countdownZenDuration;
            MetricsLogger.action(this.mContext, 1342, newZenDuration);
        } else if (checkedRadioButtonId == 2) {
            newZenDuration = -1;
            MetricsLogger.action(this.mContext, 1344);
        }
        if (currZenDuration != newZenDuration) {
            Settings.Secure.putInt(this.mContext.getContentResolver(), "zen_duration", newZenDuration);
        }
    }

    @VisibleForTesting
    protected View getContentView() {
        if (this.mLayoutInflater == null) {
            this.mLayoutInflater = new PhoneWindow(this.mContext).getLayoutInflater();
        }
        View contentView = this.mLayoutInflater.inflate(R.layout.zen_mode_duration_dialog, (ViewGroup) null);
        ScrollView container = (ScrollView) contentView.findViewById(R.id.zen_duration_container);
        this.mZenRadioGroup = (RadioGroup) container.findViewById(R.id.zen_radio_buttons);
        this.mZenRadioGroupContent = (LinearLayout) container.findViewById(R.id.zen_radio_buttons_content);
        for (int i = 0; i < this.MAX_MANUAL_DND_OPTIONS; i++) {
            View radioButton = this.mLayoutInflater.inflate(R.layout.zen_mode_radio_button, (ViewGroup) this.mZenRadioGroup, false);
            this.mZenRadioGroup.addView(radioButton);
            radioButton.setId(i);
            View radioButtonContent = this.mLayoutInflater.inflate(R.layout.zen_mode_condition, (ViewGroup) this.mZenRadioGroupContent, false);
            radioButtonContent.setId(this.MAX_MANUAL_DND_OPTIONS + i);
            this.mZenRadioGroupContent.addView(radioButtonContent);
        }
        return contentView;
    }

    @VisibleForTesting
    protected void setupRadioButtons(int zenDuration) {
        int checkedIndex = 2;
        if (zenDuration == 0) {
            checkedIndex = 0;
        } else if (zenDuration > 0) {
            checkedIndex = 1;
        }
        bindTag(zenDuration, this.mZenRadioGroupContent.getChildAt(0), 0);
        bindTag(zenDuration, this.mZenRadioGroupContent.getChildAt(1), 1);
        bindTag(zenDuration, this.mZenRadioGroupContent.getChildAt(2), 2);
        getConditionTagAt(checkedIndex).rb.setChecked(true);
    }

    private void bindTag(int currZenDuration, View row, int rowIndex) {
        final ConditionTag tag = row.getTag() != null ? (ConditionTag) row.getTag() : new ConditionTag();
        row.setTag(tag);
        if (tag.rb == null) {
            tag.rb = (RadioButton) this.mZenRadioGroup.getChildAt(rowIndex);
        }
        if (currZenDuration <= 0) {
            tag.countdownZenDuration = MINUTE_BUCKETS[DEFAULT_BUCKET_INDEX];
        } else {
            tag.countdownZenDuration = currZenDuration;
        }
        tag.rb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.android.settingslib.notification.ZenDurationDialog.2
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    tag.rb.setChecked(true);
                }
            }
        });
        updateUi(tag, row, rowIndex);
    }

    @VisibleForTesting
    protected ConditionTag getConditionTagAt(int index) {
        return (ConditionTag) this.mZenRadioGroupContent.getChildAt(index).getTag();
    }

    private void setupUi(final ConditionTag tag, View row) {
        if (tag.lines == null) {
            tag.lines = row.findViewById(16908290);
        }
        if (tag.line1 == null) {
            tag.line1 = (TextView) row.findViewById(16908308);
        }
        row.findViewById(16908309).setVisibility(8);
        tag.lines.setOnClickListener(new View.OnClickListener() { // from class: com.android.settingslib.notification.ZenDurationDialog.3
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                tag.rb.setChecked(true);
            }
        });
    }

    private void updateButtons(final ConditionTag tag, final View row, final int rowIndex) {
        boolean z;
        ImageView button1 = (ImageView) row.findViewById(16908313);
        button1.setOnClickListener(new View.OnClickListener() { // from class: com.android.settingslib.notification.ZenDurationDialog.4
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                ZenDurationDialog.this.onClickTimeButton(row, tag, false, rowIndex);
            }
        });
        ImageView button2 = (ImageView) row.findViewById(16908314);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.android.settingslib.notification.ZenDurationDialog.5
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                ZenDurationDialog.this.onClickTimeButton(row, tag, true, rowIndex);
            }
        });
        long time = tag.countdownZenDuration;
        boolean z2 = true;
        if (rowIndex == 1) {
            button1.setVisibility(0);
            button2.setVisibility(0);
            if (time > MIN_BUCKET_MINUTES) {
                z = true;
            } else {
                z = false;
            }
            button1.setEnabled(z);
            if (tag.countdownZenDuration == MAX_BUCKET_MINUTES) {
                z2 = false;
            }
            button2.setEnabled(z2);
            button1.setAlpha(button1.isEnabled() ? 1.0f : 0.5f);
            button2.setAlpha(button2.isEnabled() ? 1.0f : 0.5f);
            return;
        }
        button1.setVisibility(8);
        button2.setVisibility(8);
    }

    @VisibleForTesting
    protected void updateUi(ConditionTag tag, View row, int rowIndex) {
        if (tag.lines == null) {
            setupUi(tag, row);
        }
        updateButtons(tag, row, rowIndex);
        String radioContentText = "";
        if (rowIndex == 0) {
            radioContentText = this.mContext.getString(R.string.zen_mode_forever);
        } else if (rowIndex == 1) {
            Condition condition = ZenModeConfig.toTimeCondition(this.mContext, tag.countdownZenDuration, ActivityManager.getCurrentUser(), false);
            radioContentText = condition.line1;
        } else if (rowIndex == 2) {
            radioContentText = this.mContext.getString(R.string.zen_mode_duration_always_prompt_title);
        }
        tag.line1.setText(radioContentText);
    }

    @VisibleForTesting
    protected void onClickTimeButton(View row, ConditionTag tag, boolean up, int rowId) {
        int newDndTimeDuration = -1;
        int N = MINUTE_BUCKETS.length;
        int i = this.mBucketIndex;
        if (i == -1) {
            long time = tag.countdownZenDuration;
            for (int i2 = 0; i2 < N; i2++) {
                int j = up ? i2 : (N - 1) - i2;
                int bucketMinutes = MINUTE_BUCKETS[j];
                if ((up && bucketMinutes > time) || (!up && bucketMinutes < time)) {
                    this.mBucketIndex = j;
                    newDndTimeDuration = bucketMinutes;
                    break;
                }
            }
            if (newDndTimeDuration == -1) {
                this.mBucketIndex = DEFAULT_BUCKET_INDEX;
                newDndTimeDuration = MINUTE_BUCKETS[this.mBucketIndex];
            }
        } else {
            this.mBucketIndex = Math.max(0, Math.min(N - 1, i + (up ? 1 : -1)));
            newDndTimeDuration = MINUTE_BUCKETS[this.mBucketIndex];
        }
        tag.countdownZenDuration = newDndTimeDuration;
        bindTag(newDndTimeDuration, row, rowId);
        tag.rb.setChecked(true);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static class ConditionTag {
        public int countdownZenDuration;
        public TextView line1;
        public View lines;
        public RadioButton rb;

        protected ConditionTag() {
        }
    }
}
