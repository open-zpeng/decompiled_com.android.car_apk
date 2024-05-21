package com.android.settingslib.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
/* loaded from: classes3.dex */
public class ActionButtonsPreference extends Preference {
    private static final String TAG = "ActionButtonPreference";
    private final ButtonInfo mButton1Info;
    private final ButtonInfo mButton2Info;
    private final ButtonInfo mButton3Info;
    private final ButtonInfo mButton4Info;

    public ActionButtonsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mButton1Info = new ButtonInfo();
        this.mButton2Info = new ButtonInfo();
        this.mButton3Info = new ButtonInfo();
        this.mButton4Info = new ButtonInfo();
        init();
    }

    public ActionButtonsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mButton1Info = new ButtonInfo();
        this.mButton2Info = new ButtonInfo();
        this.mButton3Info = new ButtonInfo();
        this.mButton4Info = new ButtonInfo();
        init();
    }

    public ActionButtonsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mButton1Info = new ButtonInfo();
        this.mButton2Info = new ButtonInfo();
        this.mButton3Info = new ButtonInfo();
        this.mButton4Info = new ButtonInfo();
        init();
    }

    public ActionButtonsPreference(Context context) {
        super(context);
        this.mButton1Info = new ButtonInfo();
        this.mButton2Info = new ButtonInfo();
        this.mButton3Info = new ButtonInfo();
        this.mButton4Info = new ButtonInfo();
        init();
    }

    private void init() {
        setLayoutResource(R.layout.settings_action_buttons);
        setSelectable(false);
    }

    @Override // androidx.preference.Preference
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(true);
        holder.setDividerAllowedBelow(true);
        this.mButton1Info.mButton = (Button) holder.findViewById(R.id.button1);
        this.mButton2Info.mButton = (Button) holder.findViewById(R.id.button2);
        this.mButton3Info.mButton = (Button) holder.findViewById(R.id.button3);
        this.mButton4Info.mButton = (Button) holder.findViewById(R.id.button4);
        this.mButton1Info.setUpButton();
        this.mButton2Info.setUpButton();
        this.mButton3Info.setUpButton();
        this.mButton4Info.setUpButton();
    }

    public ActionButtonsPreference setButton1Visible(boolean isVisible) {
        if (isVisible != this.mButton1Info.mIsVisible) {
            this.mButton1Info.mIsVisible = isVisible;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton1Text(@StringRes int textResId) {
        String newText = getContext().getString(textResId);
        if (!TextUtils.equals(newText, this.mButton1Info.mText)) {
            this.mButton1Info.mText = newText;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton1Icon(@DrawableRes int iconResId) {
        if (iconResId == 0) {
            return this;
        }
        try {
            Drawable icon = getContext().getDrawable(iconResId);
            this.mButton1Info.mIcon = icon;
            notifyChanged();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Resource does not exist: " + iconResId);
        }
        return this;
    }

    public ActionButtonsPreference setButton1Enabled(boolean isEnabled) {
        if (isEnabled != this.mButton1Info.mIsEnabled) {
            this.mButton1Info.mIsEnabled = isEnabled;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton1OnClickListener(View.OnClickListener listener) {
        if (listener != this.mButton1Info.mListener) {
            this.mButton1Info.mListener = listener;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton2Visible(boolean isVisible) {
        if (isVisible != this.mButton2Info.mIsVisible) {
            this.mButton2Info.mIsVisible = isVisible;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton2Text(@StringRes int textResId) {
        String newText = getContext().getString(textResId);
        if (!TextUtils.equals(newText, this.mButton2Info.mText)) {
            this.mButton2Info.mText = newText;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton2Icon(@DrawableRes int iconResId) {
        if (iconResId == 0) {
            return this;
        }
        try {
            Drawable icon = getContext().getDrawable(iconResId);
            this.mButton2Info.mIcon = icon;
            notifyChanged();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Resource does not exist: " + iconResId);
        }
        return this;
    }

    public ActionButtonsPreference setButton2Enabled(boolean isEnabled) {
        if (isEnabled != this.mButton2Info.mIsEnabled) {
            this.mButton2Info.mIsEnabled = isEnabled;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton2OnClickListener(View.OnClickListener listener) {
        if (listener != this.mButton2Info.mListener) {
            this.mButton2Info.mListener = listener;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton3Visible(boolean isVisible) {
        if (isVisible != this.mButton3Info.mIsVisible) {
            this.mButton3Info.mIsVisible = isVisible;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton3Text(@StringRes int textResId) {
        String newText = getContext().getString(textResId);
        if (!TextUtils.equals(newText, this.mButton3Info.mText)) {
            this.mButton3Info.mText = newText;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton3Icon(@DrawableRes int iconResId) {
        if (iconResId == 0) {
            return this;
        }
        try {
            Drawable icon = getContext().getDrawable(iconResId);
            this.mButton3Info.mIcon = icon;
            notifyChanged();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Resource does not exist: " + iconResId);
        }
        return this;
    }

    public ActionButtonsPreference setButton3Enabled(boolean isEnabled) {
        if (isEnabled != this.mButton3Info.mIsEnabled) {
            this.mButton3Info.mIsEnabled = isEnabled;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton3OnClickListener(View.OnClickListener listener) {
        if (listener != this.mButton3Info.mListener) {
            this.mButton3Info.mListener = listener;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton4Visible(boolean isVisible) {
        if (isVisible != this.mButton4Info.mIsVisible) {
            this.mButton4Info.mIsVisible = isVisible;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton4Text(@StringRes int textResId) {
        String newText = getContext().getString(textResId);
        if (!TextUtils.equals(newText, this.mButton4Info.mText)) {
            this.mButton4Info.mText = newText;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton4Icon(@DrawableRes int iconResId) {
        if (iconResId == 0) {
            return this;
        }
        try {
            Drawable icon = getContext().getDrawable(iconResId);
            this.mButton4Info.mIcon = icon;
            notifyChanged();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Resource does not exist: " + iconResId);
        }
        return this;
    }

    public ActionButtonsPreference setButton4Enabled(boolean isEnabled) {
        if (isEnabled != this.mButton4Info.mIsEnabled) {
            this.mButton4Info.mIsEnabled = isEnabled;
            notifyChanged();
        }
        return this;
    }

    public ActionButtonsPreference setButton4OnClickListener(View.OnClickListener listener) {
        if (listener != this.mButton4Info.mListener) {
            this.mButton4Info.mListener = listener;
            notifyChanged();
        }
        return this;
    }

    /* loaded from: classes3.dex */
    static class ButtonInfo {
        private Button mButton;
        private Drawable mIcon;
        private boolean mIsEnabled = true;
        private boolean mIsVisible = true;
        private View.OnClickListener mListener;
        private CharSequence mText;

        ButtonInfo() {
        }

        void setUpButton() {
            this.mButton.setText(this.mText);
            this.mButton.setOnClickListener(this.mListener);
            this.mButton.setEnabled(this.mIsEnabled);
            this.mButton.setCompoundDrawablesWithIntrinsicBounds((Drawable) null, this.mIcon, (Drawable) null, (Drawable) null);
            if (shouldBeVisible()) {
                this.mButton.setVisibility(0);
            } else {
                this.mButton.setVisibility(8);
            }
        }

        private boolean shouldBeVisible() {
            return this.mIsVisible && !(TextUtils.isEmpty(this.mText) && this.mIcon == null);
        }
    }
}
