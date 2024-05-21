package com.android.settingslib;

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import com.android.internal.widget.LockPatternUtils;
import com.android.settingslib.RestrictedLockUtils;
import java.util.List;
/* loaded from: classes3.dex */
public class RestrictedLockUtilsInternal extends RestrictedLockUtils {
    private static final String LOG_TAG = "RestrictedLockUtils";
    @VisibleForTesting
    static Proxy sProxy = new Proxy();

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public interface LockSettingCheck {
        boolean isEnforcing(DevicePolicyManager devicePolicyManager, ComponentName componentName, int i);
    }

    public static Drawable getRestrictedPadlock(Context context) {
        Drawable restrictedPadlock = context.getDrawable(17301684);
        int iconSize = context.getResources().getDimensionPixelSize(17104903);
        TypedArray ta = context.obtainStyledAttributes(new int[]{16843829});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        restrictedPadlock.setTint(colorAccent);
        restrictedPadlock.setBounds(0, 0, iconSize, iconSize);
        return restrictedPadlock;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfRestrictionEnforced(Context context, String userRestriction, int userId) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null) {
            return null;
        }
        UserManager um = UserManager.get(context);
        List<UserManager.EnforcingUser> enforcingUsers = um.getUserRestrictionSources(userRestriction, UserHandle.of(userId));
        if (enforcingUsers.isEmpty()) {
            return null;
        }
        if (enforcingUsers.size() > 1) {
            return RestrictedLockUtils.EnforcedAdmin.createDefaultEnforcedAdminWithRestriction(userRestriction);
        }
        int restrictionSource = enforcingUsers.get(0).getUserRestrictionSource();
        int adminUserId = enforcingUsers.get(0).getUserHandle().getIdentifier();
        if (restrictionSource == 4) {
            if (adminUserId == userId) {
                return getProfileOwner(context, userRestriction, adminUserId);
            }
            UserInfo parentUser = um.getProfileParent(adminUserId);
            if (parentUser != null && parentUser.id == userId) {
                return getProfileOwner(context, userRestriction, adminUserId);
            }
            return RestrictedLockUtils.EnforcedAdmin.createDefaultEnforcedAdminWithRestriction(userRestriction);
        } else if (restrictionSource != 2) {
            return null;
        } else {
            if (adminUserId == userId) {
                return getDeviceOwner(context, userRestriction);
            }
            return RestrictedLockUtils.EnforcedAdmin.createDefaultEnforcedAdminWithRestriction(userRestriction);
        }
    }

    public static boolean hasBaseUserRestriction(Context context, String userRestriction, int userId) {
        UserManager um = (UserManager) context.getSystemService("user");
        return um.hasBaseUserRestriction(userRestriction, UserHandle.of(userId));
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfKeyguardFeaturesDisabled(Context context, final int keyguardFeatures, final int userId) {
        LockSettingCheck check = new LockSettingCheck() { // from class: com.android.settingslib.-$$Lambda$RestrictedLockUtilsInternal$k8iDcwhE4SvyxIn63ehYr-tUNvI
            @Override // com.android.settingslib.RestrictedLockUtilsInternal.LockSettingCheck
            public final boolean isEnforcing(DevicePolicyManager devicePolicyManager, ComponentName componentName, int i) {
                return RestrictedLockUtilsInternal.lambda$checkIfKeyguardFeaturesDisabled$0(userId, keyguardFeatures, devicePolicyManager, componentName, i);
            }
        };
        if (UserManager.get(context).getUserInfo(userId).isManagedProfile()) {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
            return findEnforcedAdmin(dpm.getActiveAdminsAsUser(userId), dpm, userId, check);
        }
        return checkForLockSetting(context, userId, check);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$checkIfKeyguardFeaturesDisabled$0(int userId, int keyguardFeatures, DevicePolicyManager dpm, ComponentName admin, int checkUser) {
        int effectiveFeatures = dpm.getKeyguardDisabledFeatures(admin, checkUser);
        if (checkUser != userId) {
            effectiveFeatures &= 432;
        }
        return (effectiveFeatures & keyguardFeatures) != 0;
    }

    private static UserHandle getUserHandleOf(int userId) {
        if (userId == -10000) {
            return null;
        }
        return UserHandle.of(userId);
    }

    private static RestrictedLockUtils.EnforcedAdmin findEnforcedAdmin(List<ComponentName> admins, DevicePolicyManager dpm, int userId, LockSettingCheck check) {
        if (admins == null) {
            return null;
        }
        UserHandle user = getUserHandleOf(userId);
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin = null;
        for (ComponentName admin : admins) {
            if (check.isEnforcing(dpm, admin, userId)) {
                if (enforcedAdmin == null) {
                    enforcedAdmin = new RestrictedLockUtils.EnforcedAdmin(admin, user);
                } else {
                    return RestrictedLockUtils.EnforcedAdmin.MULTIPLE_ENFORCED_ADMIN;
                }
            }
        }
        return enforcedAdmin;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfUninstallBlocked(Context context, String packageName, int userId) {
        RestrictedLockUtils.EnforcedAdmin allAppsControlDisallowedAdmin = checkIfRestrictionEnforced(context, "no_control_apps", userId);
        if (allAppsControlDisallowedAdmin != null) {
            return allAppsControlDisallowedAdmin;
        }
        RestrictedLockUtils.EnforcedAdmin allAppsUninstallDisallowedAdmin = checkIfRestrictionEnforced(context, "no_uninstall_apps", userId);
        if (allAppsUninstallDisallowedAdmin != null) {
            return allAppsUninstallDisallowedAdmin;
        }
        IPackageManager ipm = AppGlobals.getPackageManager();
        try {
            if (ipm.getBlockUninstallForUser(packageName, userId)) {
                return getProfileOrDeviceOwner(context, getUserHandleOf(userId));
            }
            return null;
        } catch (RemoteException e) {
            return null;
        }
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfApplicationIsSuspended(Context context, String packageName, int userId) {
        IPackageManager ipm = AppGlobals.getPackageManager();
        try {
            if (ipm.isPackageSuspendedForUser(packageName, userId)) {
                return getProfileOrDeviceOwner(context, getUserHandleOf(userId));
            }
            return null;
        } catch (RemoteException | IllegalArgumentException e) {
            return null;
        }
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfInputMethodDisallowed(Context context, String packageName, int userId) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null) {
            return null;
        }
        RestrictedLockUtils.EnforcedAdmin admin = getProfileOrDeviceOwner(context, getUserHandleOf(userId));
        boolean permitted = true;
        if (admin != null) {
            permitted = dpm.isInputMethodPermittedByAdmin(admin.component, packageName, userId);
        }
        int managedProfileId = getManagedProfileId(context, userId);
        RestrictedLockUtils.EnforcedAdmin profileAdmin = getProfileOrDeviceOwner(context, getUserHandleOf(managedProfileId));
        boolean permittedByProfileAdmin = true;
        if (profileAdmin != null) {
            permittedByProfileAdmin = dpm.isInputMethodPermittedByAdmin(profileAdmin.component, packageName, managedProfileId);
        }
        if (!permitted && !permittedByProfileAdmin) {
            return RestrictedLockUtils.EnforcedAdmin.MULTIPLE_ENFORCED_ADMIN;
        }
        if (!permitted) {
            return admin;
        }
        if (permittedByProfileAdmin) {
            return null;
        }
        return profileAdmin;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfRemoteContactSearchDisallowed(Context context, int userId) {
        RestrictedLockUtils.EnforcedAdmin admin;
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null || (admin = getProfileOwner(context, userId)) == null) {
            return null;
        }
        UserHandle userHandle = UserHandle.of(userId);
        if (!dpm.getCrossProfileContactsSearchDisabled(userHandle) || !dpm.getCrossProfileCallerIdDisabled(userHandle)) {
            return null;
        }
        return admin;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfAccessibilityServiceDisallowed(Context context, String packageName, int userId) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null) {
            return null;
        }
        RestrictedLockUtils.EnforcedAdmin admin = getProfileOrDeviceOwner(context, getUserHandleOf(userId));
        boolean permitted = true;
        if (admin != null) {
            permitted = dpm.isAccessibilityServicePermittedByAdmin(admin.component, packageName, userId);
        }
        int managedProfileId = getManagedProfileId(context, userId);
        RestrictedLockUtils.EnforcedAdmin profileAdmin = getProfileOrDeviceOwner(context, getUserHandleOf(managedProfileId));
        boolean permittedByProfileAdmin = true;
        if (profileAdmin != null) {
            permittedByProfileAdmin = dpm.isAccessibilityServicePermittedByAdmin(profileAdmin.component, packageName, managedProfileId);
        }
        if (!permitted && !permittedByProfileAdmin) {
            return RestrictedLockUtils.EnforcedAdmin.MULTIPLE_ENFORCED_ADMIN;
        }
        if (!permitted) {
            return admin;
        }
        if (permittedByProfileAdmin) {
            return null;
        }
        return profileAdmin;
    }

    private static int getManagedProfileId(Context context, int userId) {
        UserManager um = (UserManager) context.getSystemService("user");
        List<UserInfo> userProfiles = um.getProfiles(userId);
        for (UserInfo uInfo : userProfiles) {
            if (uInfo.id != userId && uInfo.isManagedProfile()) {
                return uInfo.id;
            }
        }
        return -10000;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfAccountManagementDisabled(Context context, String accountType, int userId) {
        if (accountType == null) {
            return null;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        PackageManager pm = context.getPackageManager();
        if (!pm.hasSystemFeature("android.software.device_admin") || dpm == null) {
            return null;
        }
        boolean isAccountTypeDisabled = false;
        String[] disabledTypes = dpm.getAccountTypesWithManagementDisabledAsUser(userId);
        int length = disabledTypes.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                break;
            }
            String type = disabledTypes[i];
            if (!accountType.equals(type)) {
                i++;
            } else {
                isAccountTypeDisabled = true;
                break;
            }
        }
        if (!isAccountTypeDisabled) {
            return null;
        }
        return getProfileOrDeviceOwner(context, getUserHandleOf(userId));
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfMeteredDataRestricted(Context context, String packageName, int userId) {
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin = getProfileOrDeviceOwner(context, getUserHandleOf(userId));
        if (enforcedAdmin == null) {
            return null;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (!dpm.isMeteredDataDisabledPackageForUser(enforcedAdmin.component, packageName, userId)) {
            return null;
        }
        return enforcedAdmin;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfAutoTimeRequired(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null || !dpm.getAutoTimeRequired()) {
            return null;
        }
        ComponentName adminComponent = dpm.getDeviceOwnerComponentOnCallingUser();
        return new RestrictedLockUtils.EnforcedAdmin(adminComponent, getUserHandleOf(UserHandle.myUserId()));
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfPasswordQualityIsSet(Context context, int userId) {
        LockSettingCheck check = new LockSettingCheck() { // from class: com.android.settingslib.-$$Lambda$RestrictedLockUtilsInternal$yvS34yJS2kpTNeXUsuaEu-8yH1g
            @Override // com.android.settingslib.RestrictedLockUtilsInternal.LockSettingCheck
            public final boolean isEnforcing(DevicePolicyManager devicePolicyManager, ComponentName componentName, int i) {
                return RestrictedLockUtilsInternal.lambda$checkIfPasswordQualityIsSet$1(devicePolicyManager, componentName, i);
            }
        };
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null) {
            return null;
        }
        LockPatternUtils lockPatternUtils = new LockPatternUtils(context);
        if (sProxy.isSeparateProfileChallengeEnabled(lockPatternUtils, userId)) {
            List<ComponentName> admins = dpm.getActiveAdminsAsUser(userId);
            if (admins == null) {
                return null;
            }
            RestrictedLockUtils.EnforcedAdmin enforcedAdmin = null;
            UserHandle user = getUserHandleOf(userId);
            for (ComponentName admin : admins) {
                if (check.isEnforcing(dpm, admin, userId)) {
                    if (enforcedAdmin == null) {
                        enforcedAdmin = new RestrictedLockUtils.EnforcedAdmin(admin, user);
                    } else {
                        return RestrictedLockUtils.EnforcedAdmin.MULTIPLE_ENFORCED_ADMIN;
                    }
                }
            }
            return enforcedAdmin;
        }
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin2 = checkForLockSetting(context, userId, check);
        return enforcedAdmin2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$checkIfPasswordQualityIsSet$1(DevicePolicyManager dpm, ComponentName admin, int checkUser) {
        return dpm.getPasswordQuality(admin, checkUser) > 0;
    }

    public static RestrictedLockUtils.EnforcedAdmin checkIfMaximumTimeToLockIsSet(Context context) {
        return checkForLockSetting(context, UserHandle.myUserId(), new LockSettingCheck() { // from class: com.android.settingslib.-$$Lambda$RestrictedLockUtilsInternal$GXYFzBzGab6v5GcOkljXViw5O7I
            @Override // com.android.settingslib.RestrictedLockUtilsInternal.LockSettingCheck
            public final boolean isEnforcing(DevicePolicyManager devicePolicyManager, ComponentName componentName, int i) {
                return RestrictedLockUtilsInternal.lambda$checkIfMaximumTimeToLockIsSet$2(devicePolicyManager, componentName, i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$checkIfMaximumTimeToLockIsSet$2(DevicePolicyManager dpm, ComponentName admin, int userId) {
        return dpm.getMaximumTimeToLock(admin, userId) > 0;
    }

    private static RestrictedLockUtils.EnforcedAdmin checkForLockSetting(Context context, int userId, LockSettingCheck check) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null) {
            return null;
        }
        LockPatternUtils lockPatternUtils = new LockPatternUtils(context);
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin = null;
        for (UserInfo userInfo : UserManager.get(context).getProfiles(userId)) {
            List<ComponentName> admins = dpm.getActiveAdminsAsUser(userInfo.id);
            if (admins != null) {
                UserHandle user = getUserHandleOf(userInfo.id);
                boolean isSeparateProfileChallengeEnabled = sProxy.isSeparateProfileChallengeEnabled(lockPatternUtils, userInfo.id);
                for (ComponentName admin : admins) {
                    if (!isSeparateProfileChallengeEnabled && check.isEnforcing(dpm, admin, userInfo.id)) {
                        if (enforcedAdmin == null) {
                            enforcedAdmin = new RestrictedLockUtils.EnforcedAdmin(admin, user);
                        } else {
                            return RestrictedLockUtils.EnforcedAdmin.MULTIPLE_ENFORCED_ADMIN;
                        }
                    } else if (userInfo.isManagedProfile()) {
                        DevicePolicyManager parentDpm = sProxy.getParentProfileInstance(dpm, userInfo);
                        if (!check.isEnforcing(parentDpm, admin, userInfo.id)) {
                            continue;
                        } else if (enforcedAdmin == null) {
                            enforcedAdmin = new RestrictedLockUtils.EnforcedAdmin(admin, user);
                        } else {
                            return RestrictedLockUtils.EnforcedAdmin.MULTIPLE_ENFORCED_ADMIN;
                        }
                    } else {
                        continue;
                    }
                }
                continue;
            }
        }
        return enforcedAdmin;
    }

    public static RestrictedLockUtils.EnforcedAdmin getDeviceOwner(Context context) {
        return getDeviceOwner(context, null);
    }

    private static RestrictedLockUtils.EnforcedAdmin getDeviceOwner(Context context, String enforcedRestriction) {
        ComponentName adminComponent;
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm == null || (adminComponent = dpm.getDeviceOwnerComponentOnAnyUser()) == null) {
            return null;
        }
        return new RestrictedLockUtils.EnforcedAdmin(adminComponent, enforcedRestriction, dpm.getDeviceOwnerUser());
    }

    private static RestrictedLockUtils.EnforcedAdmin getProfileOwner(Context context, int userId) {
        return getProfileOwner(context, null, userId);
    }

    private static RestrictedLockUtils.EnforcedAdmin getProfileOwner(Context context, String enforcedRestriction, int userId) {
        DevicePolicyManager dpm;
        ComponentName adminComponent;
        if (userId == -10000 || (dpm = (DevicePolicyManager) context.getSystemService("device_policy")) == null || (adminComponent = dpm.getProfileOwnerAsUser(userId)) == null) {
            return null;
        }
        return new RestrictedLockUtils.EnforcedAdmin(adminComponent, enforcedRestriction, getUserHandleOf(userId));
    }

    public static void setMenuItemAsDisabledByAdmin(final Context context, MenuItem item, final RestrictedLockUtils.EnforcedAdmin admin) {
        SpannableStringBuilder sb = new SpannableStringBuilder(item.getTitle());
        removeExistingRestrictedSpans(sb);
        if (admin != null) {
            int disabledColor = context.getColor(R.color.disabled_text_color);
            sb.setSpan(new ForegroundColorSpan(disabledColor), 0, sb.length(), 33);
            ImageSpan image = new RestrictedLockImageSpan(context);
            sb.append(" ", image, 33);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() { // from class: com.android.settingslib.RestrictedLockUtilsInternal.1
                @Override // android.view.MenuItem.OnMenuItemClickListener
                public boolean onMenuItemClick(MenuItem item2) {
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(context, admin);
                    return true;
                }
            });
        } else {
            item.setOnMenuItemClickListener(null);
        }
        item.setTitle(sb);
    }

    private static void removeExistingRestrictedSpans(SpannableStringBuilder sb) {
        int length = sb.length();
        ImageSpan[] imageSpans = (RestrictedLockImageSpan[]) sb.getSpans(length - 1, length, RestrictedLockImageSpan.class);
        for (ImageSpan span : imageSpans) {
            int start = sb.getSpanStart(span);
            int end = sb.getSpanEnd(span);
            sb.removeSpan(span);
            sb.delete(start, end);
        }
        ForegroundColorSpan[] colorSpans = (ForegroundColorSpan[]) sb.getSpans(0, length, ForegroundColorSpan.class);
        for (ForegroundColorSpan span2 : colorSpans) {
            sb.removeSpan(span2);
        }
    }

    public static boolean isAdminInCurrentUserOrProfile(Context context, ComponentName admin) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        UserManager um = UserManager.get(context);
        for (UserInfo userInfo : um.getProfiles(UserHandle.myUserId())) {
            if (dpm.isAdminActiveAsUser(admin, userInfo.id)) {
                return true;
            }
        }
        return false;
    }

    public static void setTextViewPadlock(Context context, TextView textView, boolean showPadlock) {
        SpannableStringBuilder sb = new SpannableStringBuilder(textView.getText());
        removeExistingRestrictedSpans(sb);
        if (showPadlock) {
            ImageSpan image = new RestrictedLockImageSpan(context);
            sb.append(" ", image, 33);
        }
        textView.setText(sb);
    }

    public static void setTextViewAsDisabledByAdmin(Context context, TextView textView, boolean disabled) {
        SpannableStringBuilder sb = new SpannableStringBuilder(textView.getText());
        removeExistingRestrictedSpans(sb);
        if (disabled) {
            int disabledColor = context.getColor(R.color.disabled_text_color);
            sb.setSpan(new ForegroundColorSpan(disabledColor), 0, sb.length(), 33);
            textView.setCompoundDrawables(null, null, getRestrictedPadlock(context), null);
            textView.setCompoundDrawablePadding(context.getResources().getDimensionPixelSize(R.dimen.restricted_icon_padding));
        } else {
            textView.setCompoundDrawables(null, null, null, null);
        }
        textView.setText(sb);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static class Proxy {
        Proxy() {
        }

        public boolean isSeparateProfileChallengeEnabled(LockPatternUtils utils, int userHandle) {
            return utils.isSeparateProfileChallengeEnabled(userHandle);
        }

        public DevicePolicyManager getParentProfileInstance(DevicePolicyManager dpm, UserInfo ui) {
            return dpm.getParentProfileInstance(ui);
        }
    }
}
