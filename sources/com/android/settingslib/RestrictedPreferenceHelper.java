package com.android.settingslib;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.android.settingslib.RestrictedLockUtils;
/* loaded from: classes3.dex */
public class RestrictedPreferenceHelper {
    private String mAttrUserRestriction;
    private final Context mContext;
    private boolean mDisabledByAdmin;
    private RestrictedLockUtils.EnforcedAdmin mEnforcedAdmin;
    private final Preference mPreference;
    private boolean mUseAdminDisabledSummary;

    public RestrictedPreferenceHelper(Context context, Preference preference, AttributeSet attrs) {
        this.mAttrUserRestriction = null;
        boolean z = false;
        this.mUseAdminDisabledSummary = false;
        this.mContext = context;
        this.mPreference = preference;
        if (attrs != null) {
            TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RestrictedPreference);
            TypedValue userRestriction = attributes.peekValue(R.styleable.RestrictedPreference_userRestriction);
            CharSequence data = null;
            if (userRestriction != null && userRestriction.type == 3) {
                data = userRestriction.resourceId != 0 ? context.getText(userRestriction.resourceId) : userRestriction.string;
            }
            this.mAttrUserRestriction = data == null ? null : data.toString();
            if (RestrictedLockUtilsInternal.hasBaseUserRestriction(this.mContext, this.mAttrUserRestriction, UserHandle.myUserId())) {
                this.mAttrUserRestriction = null;
                return;
            }
            TypedValue useAdminDisabledSummary = attributes.peekValue(R.styleable.RestrictedPreference_useAdminDisabledSummary);
            if (useAdminDisabledSummary != null) {
                if (useAdminDisabledSummary.type == 18 && useAdminDisabledSummary.data != 0) {
                    z = true;
                }
                this.mUseAdminDisabledSummary = z;
            }
        }
    }

    public void onBindViewHolder(PreferenceViewHolder holder) {
        TextView summaryView;
        if (this.mDisabledByAdmin) {
            holder.itemView.setEnabled(true);
        }
        if (this.mUseAdminDisabledSummary && (summaryView = (TextView) holder.findViewById(16908304)) != null) {
            CharSequence disabledText = summaryView.getContext().getText(R.string.disabled_by_admin_summary_text);
            if (this.mDisabledByAdmin) {
                summaryView.setText(disabledText);
            } else if (TextUtils.equals(disabledText, summaryView.getText())) {
                summaryView.setText((CharSequence) null);
            }
        }
    }

    public void useAdminDisabledSummary(boolean useSummary) {
        this.mUseAdminDisabledSummary = useSummary;
    }

    public boolean performClick() {
        if (this.mDisabledByAdmin) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(this.mContext, this.mEnforcedAdmin);
            return true;
        }
        return false;
    }

    public void onAttachedToHierarchy() {
        String str = this.mAttrUserRestriction;
        if (str != null) {
            checkRestrictionAndSetDisabled(str, UserHandle.myUserId());
        }
    }

    public void checkRestrictionAndSetDisabled(String userRestriction, int userId) {
        RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(this.mContext, userRestriction, userId);
        setDisabledByAdmin(admin);
    }

    public boolean setDisabledByAdmin(RestrictedLockUtils.EnforcedAdmin admin) {
        boolean disabled = admin != null;
        this.mEnforcedAdmin = admin;
        boolean changed = false;
        if (this.mDisabledByAdmin != disabled) {
            this.mDisabledByAdmin = disabled;
            changed = true;
        }
        this.mPreference.setEnabled(disabled ? false : true);
        return changed;
    }

    public boolean isDisabledByAdmin() {
        return this.mDisabledByAdmin;
    }
}
