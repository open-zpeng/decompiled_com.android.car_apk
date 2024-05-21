package com.android.settingslib.location;

import android.content.Intent;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.annotations.Immutable;
import java.util.Objects;
@Immutable
/* loaded from: classes3.dex */
public class InjectedSetting {
    public final String className;
    public final int iconId;
    public final UserHandle mUserHandle;
    public final String packageName;
    public final String settingsActivity;
    public final String title;
    public final String userRestriction;

    private InjectedSetting(Builder builder) {
        this.packageName = builder.mPackageName;
        this.className = builder.mClassName;
        this.title = builder.mTitle;
        this.iconId = builder.mIconId;
        this.mUserHandle = builder.mUserHandle;
        this.settingsActivity = builder.mSettingsActivity;
        this.userRestriction = builder.mUserRestriction;
    }

    public String toString() {
        return "InjectedSetting{mPackageName='" + this.packageName + "', mClassName='" + this.className + "', label=" + this.title + ", iconId=" + this.iconId + ", userId=" + this.mUserHandle.getIdentifier() + ", settingsActivity='" + this.settingsActivity + "', userRestriction='" + this.userRestriction + '}';
    }

    public Intent getServiceIntent() {
        Intent intent = new Intent();
        intent.setClassName(this.packageName, this.className);
        return intent;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof InjectedSetting) {
            InjectedSetting that = (InjectedSetting) o;
            return Objects.equals(this.packageName, that.packageName) && Objects.equals(this.className, that.className) && Objects.equals(this.title, that.title) && Objects.equals(Integer.valueOf(this.iconId), Integer.valueOf(that.iconId)) && Objects.equals(this.mUserHandle, that.mUserHandle) && Objects.equals(this.settingsActivity, that.settingsActivity) && Objects.equals(this.userRestriction, that.userRestriction);
        }
        return false;
    }

    public int hashCode() {
        int result = this.packageName.hashCode();
        int result2 = ((((((result * 31) + this.className.hashCode()) * 31) + this.title.hashCode()) * 31) + this.iconId) * 31;
        UserHandle userHandle = this.mUserHandle;
        int result3 = (((result2 + (userHandle == null ? 0 : userHandle.hashCode())) * 31) + this.settingsActivity.hashCode()) * 31;
        String str = this.userRestriction;
        return result3 + (str != null ? str.hashCode() : 0);
    }

    /* loaded from: classes3.dex */
    public static class Builder {
        private String mClassName;
        private int mIconId;
        private String mPackageName;
        private String mSettingsActivity;
        private String mTitle;
        private UserHandle mUserHandle;
        private String mUserRestriction;

        public Builder setPackageName(String packageName) {
            this.mPackageName = packageName;
            return this;
        }

        public Builder setClassName(String className) {
            this.mClassName = className;
            return this;
        }

        public Builder setTitle(String title) {
            this.mTitle = title;
            return this;
        }

        public Builder setIconId(int iconId) {
            this.mIconId = iconId;
            return this;
        }

        public Builder setUserHandle(UserHandle userHandle) {
            this.mUserHandle = userHandle;
            return this;
        }

        public Builder setSettingsActivity(String settingsActivity) {
            this.mSettingsActivity = settingsActivity;
            return this;
        }

        public Builder setUserRestriction(String userRestriction) {
            this.mUserRestriction = userRestriction;
            return this;
        }

        public InjectedSetting build() {
            if (this.mPackageName == null || this.mClassName == null || TextUtils.isEmpty(this.mTitle) || TextUtils.isEmpty(this.mSettingsActivity)) {
                if (Log.isLoggable("SettingsInjector", 5)) {
                    Log.w("SettingsInjector", "Illegal setting specification: package=" + this.mPackageName + ", class=" + this.mClassName + ", title=" + this.mTitle + ", settingsActivity=" + this.mSettingsActivity);
                }
                return null;
            }
            return new InjectedSetting(this);
        }
    }
}
