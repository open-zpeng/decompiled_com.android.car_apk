package com.android.settingslib.applications;

import android.content.Context;
import android.os.Handler;
import android.permission.PermissionControllerManager;
import android.permission.RuntimePermissionPresentationInfo;
import com.android.settingslib.applications.PermissionsSummaryHelper;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/* loaded from: classes3.dex */
public class PermissionsSummaryHelper {
    public static void getPermissionSummary(Context context, String pkg, final PermissionsResultCallback callback) {
        PermissionControllerManager permController = (PermissionControllerManager) context.getSystemService(PermissionControllerManager.class);
        permController.getAppPermissions(pkg, new PermissionControllerManager.OnGetAppPermissionResultCallback() { // from class: com.android.settingslib.applications.-$$Lambda$PermissionsSummaryHelper$5KNAuDHouZhJftbqZ0g04ncINrg
            public final void onGetAppPermissions(List list) {
                PermissionsSummaryHelper.lambda$getPermissionSummary$0(PermissionsSummaryHelper.PermissionsResultCallback.this, list);
            }
        }, (Handler) null);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$getPermissionSummary$0(PermissionsResultCallback callback, List permissions) {
        int permissionCount = permissions.size();
        int grantedStandardCount = 0;
        int grantedAdditionalCount = 0;
        int requestedCount = 0;
        List<CharSequence> grantedStandardLabels = new ArrayList<>();
        for (int i = 0; i < permissionCount; i++) {
            RuntimePermissionPresentationInfo permission = (RuntimePermissionPresentationInfo) permissions.get(i);
            requestedCount++;
            if (permission.isGranted()) {
                if (permission.isStandard()) {
                    grantedStandardLabels.add(permission.getLabel());
                    grantedStandardCount++;
                } else {
                    grantedAdditionalCount++;
                }
            }
        }
        Collator collator = Collator.getInstance();
        collator.setStrength(0);
        Collections.sort(grantedStandardLabels, collator);
        callback.onPermissionSummaryResult(grantedStandardCount, requestedCount, grantedAdditionalCount, grantedStandardLabels);
    }

    /* loaded from: classes3.dex */
    public static abstract class PermissionsResultCallback {
        public void onAppWithPermissionsCountsResult(int standardGrantedPermissionAppCount, int standardUsedPermissionAppCount) {
        }

        public void onPermissionSummaryResult(int standardGrantedPermissionCount, int requestedPermissionCount, int additionalGrantedPermissionCount, List<CharSequence> grantedGroupLabels) {
        }
    }
}
