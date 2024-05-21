package com.android.settingslib.inputmethod;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Toast;
import androidx.preference.Preference;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.R;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedSwitchPreference;
import java.text.Collator;
import java.util.List;
/* loaded from: classes3.dex */
public class InputMethodPreference extends RestrictedSwitchPreference implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    private static final String EMPTY_TEXT = "";
    private static final int NO_WIDGET = 0;
    private static final String TAG = InputMethodPreference.class.getSimpleName();
    private AlertDialog mDialog;
    private final boolean mHasPriorityInSorting;
    private final InputMethodInfo mImi;
    private final InputMethodSettingValuesWrapper mInputMethodSettingValues;
    private final boolean mIsAllowedByOrganization;
    private final OnSavePreferenceListener mOnSaveListener;

    /* loaded from: classes3.dex */
    public interface OnSavePreferenceListener {
        void onSaveInputMethodPreference(InputMethodPreference inputMethodPreference);
    }

    public InputMethodPreference(Context context, InputMethodInfo imi, boolean isImeEnabler, boolean isAllowedByOrganization, OnSavePreferenceListener onSaveListener) {
        this(context, imi, imi.loadLabel(context.getPackageManager()), isAllowedByOrganization, onSaveListener);
        if (!isImeEnabler) {
            setWidgetLayoutResource(0);
        }
    }

    @VisibleForTesting
    InputMethodPreference(Context context, InputMethodInfo imi, CharSequence title, boolean isAllowedByOrganization, OnSavePreferenceListener onSaveListener) {
        super(context);
        this.mDialog = null;
        boolean z = false;
        setPersistent(false);
        this.mImi = imi;
        this.mIsAllowedByOrganization = isAllowedByOrganization;
        this.mOnSaveListener = onSaveListener;
        setSwitchTextOn(EMPTY_TEXT);
        setSwitchTextOff(EMPTY_TEXT);
        setKey(imi.getId());
        setTitle(title);
        String settingsActivity = imi.getSettingsActivity();
        if (TextUtils.isEmpty(settingsActivity)) {
            setIntent(null);
        } else {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setClassName(imi.getPackageName(), settingsActivity);
            setIntent(intent);
        }
        this.mInputMethodSettingValues = InputMethodSettingValuesWrapper.getInstance(context);
        if (imi.isSystem() && InputMethodAndSubtypeUtil.isValidNonAuxAsciiCapableIme(imi)) {
            z = true;
        }
        this.mHasPriorityInSorting = z;
        setOnPreferenceClickListener(this);
        setOnPreferenceChangeListener(this);
    }

    public InputMethodInfo getInputMethodInfo() {
        return this.mImi;
    }

    private boolean isImeEnabler() {
        return getWidgetLayoutResource() != 0;
    }

    @Override // androidx.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (isImeEnabler()) {
            if (isChecked()) {
                setCheckedInternal(false);
                return false;
            }
            if (this.mImi.isSystem()) {
                if (this.mImi.getServiceInfo().directBootAware || isTv()) {
                    setCheckedInternal(true);
                } else if (!isTv()) {
                    showDirectBootWarnDialog();
                }
            } else {
                showSecurityWarnDialog();
            }
            return false;
        }
        return false;
    }

    @Override // androidx.preference.Preference.OnPreferenceClickListener
    public boolean onPreferenceClick(Preference preference) {
        if (isImeEnabler()) {
            return true;
        }
        Context context = getContext();
        try {
            Intent intent = getIntent();
            if (intent != null) {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "IME's Settings Activity Not Found", e);
            String message = context.getString(R.string.failed_to_open_app_settings_toast, this.mImi.loadLabel(context.getPackageManager()));
            Toast.makeText(context, message, 1).show();
        }
        return true;
    }

    public void updatePreferenceViews() {
        boolean isAlwaysChecked = this.mInputMethodSettingValues.isAlwaysCheckedIme(this.mImi);
        if (isAlwaysChecked && isImeEnabler()) {
            setDisabledByAdmin(null);
            setEnabled(false);
        } else if (!this.mIsAllowedByOrganization) {
            RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfInputMethodDisallowed(getContext(), this.mImi.getPackageName(), UserHandle.myUserId());
            setDisabledByAdmin(admin);
        } else {
            setEnabled(true);
        }
        setChecked(this.mInputMethodSettingValues.isEnabledImi(this.mImi));
        if (!isDisabledByAdmin()) {
            setSummary(getSummaryString());
        }
    }

    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) getContext().getSystemService("input_method");
    }

    private String getSummaryString() {
        InputMethodManager imm = getInputMethodManager();
        List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(this.mImi, true);
        return InputMethodAndSubtypeUtil.getSubtypeLocaleNameListAsSentence(subtypes, getContext(), this.mImi);
    }

    private void setCheckedInternal(boolean checked) {
        super.setChecked(checked);
        this.mOnSaveListener.onSaveInputMethodPreference(this);
        notifyChanged();
    }

    private void showSecurityWarnDialog() {
        AlertDialog alertDialog = this.mDialog;
        if (alertDialog != null && alertDialog.isShowing()) {
            this.mDialog.dismiss();
        }
        Context context = getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(17039380);
        CharSequence label = this.mImi.getServiceInfo().applicationInfo.loadLabel(context.getPackageManager());
        builder.setMessage(context.getString(R.string.ime_security_warning, label));
        builder.setPositiveButton(17039370, new DialogInterface.OnClickListener() { // from class: com.android.settingslib.inputmethod.-$$Lambda$InputMethodPreference$pHt4-6FWRQ9Ts6PuJy_AB14MhJc
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                InputMethodPreference.this.lambda$showSecurityWarnDialog$0$InputMethodPreference(dialogInterface, i);
            }
        });
        builder.setNegativeButton(17039360, new DialogInterface.OnClickListener() { // from class: com.android.settingslib.inputmethod.-$$Lambda$InputMethodPreference$HH5dtwzFZv06UNDXJAO6Cyx4kxo
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                InputMethodPreference.this.lambda$showSecurityWarnDialog$1$InputMethodPreference(dialogInterface, i);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: com.android.settingslib.inputmethod.-$$Lambda$InputMethodPreference$hpUUW_Jm1ATEk1-GeQASyreqYZI
            @Override // android.content.DialogInterface.OnCancelListener
            public final void onCancel(DialogInterface dialogInterface) {
                InputMethodPreference.this.lambda$showSecurityWarnDialog$2$InputMethodPreference(dialogInterface);
            }
        });
        this.mDialog = builder.create();
        this.mDialog.show();
    }

    public /* synthetic */ void lambda$showSecurityWarnDialog$0$InputMethodPreference(DialogInterface dialog, int which) {
        if (this.mImi.getServiceInfo().directBootAware || isTv()) {
            setCheckedInternal(true);
        } else {
            showDirectBootWarnDialog();
        }
    }

    public /* synthetic */ void lambda$showSecurityWarnDialog$1$InputMethodPreference(DialogInterface dialog, int which) {
        setCheckedInternal(false);
    }

    public /* synthetic */ void lambda$showSecurityWarnDialog$2$InputMethodPreference(DialogInterface dialog) {
        setCheckedInternal(false);
    }

    private boolean isTv() {
        return (getContext().getResources().getConfiguration().uiMode & 15) == 4;
    }

    private void showDirectBootWarnDialog() {
        AlertDialog alertDialog = this.mDialog;
        if (alertDialog != null && alertDialog.isShowing()) {
            this.mDialog.dismiss();
        }
        Context context = getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setMessage(context.getText(R.string.direct_boot_unaware_dialog_message));
        builder.setPositiveButton(17039370, new DialogInterface.OnClickListener() { // from class: com.android.settingslib.inputmethod.-$$Lambda$InputMethodPreference$_R1WCgG1LabBNKieYWiJs9NnYv4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                InputMethodPreference.this.lambda$showDirectBootWarnDialog$3$InputMethodPreference(dialogInterface, i);
            }
        });
        builder.setNegativeButton(17039360, new DialogInterface.OnClickListener() { // from class: com.android.settingslib.inputmethod.-$$Lambda$InputMethodPreference$8Yu3IA81uQ9mforg_QOtWUG_Sj4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                InputMethodPreference.this.lambda$showDirectBootWarnDialog$4$InputMethodPreference(dialogInterface, i);
            }
        });
        this.mDialog = builder.create();
        this.mDialog.show();
    }

    public /* synthetic */ void lambda$showDirectBootWarnDialog$3$InputMethodPreference(DialogInterface dialog, int which) {
        setCheckedInternal(true);
    }

    public /* synthetic */ void lambda$showDirectBootWarnDialog$4$InputMethodPreference(DialogInterface dialog, int which) {
        setCheckedInternal(false);
    }

    public int compareTo(InputMethodPreference rhs, Collator collator) {
        if (this == rhs) {
            return 0;
        }
        boolean z = this.mHasPriorityInSorting;
        if (z != rhs.mHasPriorityInSorting) {
            return z ? -1 : 1;
        }
        CharSequence title = getTitle();
        CharSequence rhsTitle = rhs.getTitle();
        boolean emptyTitle = TextUtils.isEmpty(title);
        boolean rhsEmptyTitle = TextUtils.isEmpty(rhsTitle);
        if (emptyTitle || rhsEmptyTitle) {
            return (emptyTitle ? -1 : 0) - (rhsEmptyTitle ? -1 : 0);
        }
        return collator.compare(title.toString(), rhsTitle.toString());
    }
}
