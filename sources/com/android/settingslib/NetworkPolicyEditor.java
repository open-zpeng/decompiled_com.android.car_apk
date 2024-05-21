package com.android.settingslib;

import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.RecurrenceRule;
import com.android.internal.util.Preconditions;
import com.google.android.collect.Lists;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
/* loaded from: classes3.dex */
public class NetworkPolicyEditor {
    public static final boolean ENABLE_SPLIT_POLICIES = false;
    private ArrayList<NetworkPolicy> mPolicies = Lists.newArrayList();
    private NetworkPolicyManager mPolicyManager;

    public NetworkPolicyEditor(NetworkPolicyManager policyManager) {
        this.mPolicyManager = (NetworkPolicyManager) Preconditions.checkNotNull(policyManager);
    }

    public void read() {
        NetworkPolicy[] policies = this.mPolicyManager.getNetworkPolicies();
        boolean modified = false;
        this.mPolicies.clear();
        for (NetworkPolicy policy : policies) {
            if (policy.limitBytes < -1) {
                policy.limitBytes = -1L;
                modified = true;
            }
            if (policy.warningBytes < -1) {
                policy.warningBytes = -1L;
                modified = true;
            }
            this.mPolicies.add(policy);
        }
        if (modified) {
            writeAsync();
        }
    }

    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.settingslib.NetworkPolicyEditor$1] */
    public void writeAsync() {
        ArrayList<NetworkPolicy> arrayList = this.mPolicies;
        final NetworkPolicy[] policies = (NetworkPolicy[]) arrayList.toArray(new NetworkPolicy[arrayList.size()]);
        new AsyncTask<Void, Void, Void>() { // from class: com.android.settingslib.NetworkPolicyEditor.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // android.os.AsyncTask
            public Void doInBackground(Void... params) {
                NetworkPolicyEditor.this.write(policies);
                return null;
            }
        }.execute(new Void[0]);
    }

    public void write(NetworkPolicy[] policies) {
        this.mPolicyManager.setNetworkPolicies(policies);
    }

    public boolean hasLimitedPolicy(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        return (policy == null || policy.limitBytes == -1) ? false : true;
    }

    public NetworkPolicy getOrCreatePolicy(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        if (policy == null) {
            NetworkPolicy policy2 = buildDefaultPolicy(template);
            this.mPolicies.add(policy2);
            return policy2;
        }
        return policy;
    }

    public NetworkPolicy getPolicy(NetworkTemplate template) {
        Iterator<NetworkPolicy> it = this.mPolicies.iterator();
        while (it.hasNext()) {
            NetworkPolicy policy = it.next();
            if (policy.template.equals(template)) {
                return policy;
            }
        }
        return null;
    }

    public NetworkPolicy getPolicyMaybeUnquoted(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        if (policy != null) {
            return policy;
        }
        return getPolicy(buildUnquotedNetworkTemplate(template));
    }

    @Deprecated
    private static NetworkPolicy buildDefaultPolicy(NetworkTemplate template) {
        RecurrenceRule cycleRule;
        boolean metered;
        if (template.getMatchRule() == 4) {
            cycleRule = RecurrenceRule.buildNever();
            metered = false;
        } else {
            cycleRule = RecurrenceRule.buildRecurringMonthly(ZonedDateTime.now().getDayOfMonth(), ZoneId.systemDefault());
            metered = true;
        }
        return new NetworkPolicy(template, cycleRule, -1L, -1L, -1L, -1L, metered, true);
    }

    @Deprecated
    public int getPolicyCycleDay(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        if (policy != null && policy.cycleRule.isMonthly()) {
            return policy.cycleRule.start.getDayOfMonth();
        }
        return -1;
    }

    @Deprecated
    public void setPolicyCycleDay(NetworkTemplate template, int cycleDay, String cycleTimezone) {
        NetworkPolicy policy = getOrCreatePolicy(template);
        policy.cycleRule = NetworkPolicy.buildRule(cycleDay, ZoneId.of(cycleTimezone));
        policy.inferred = false;
        policy.clearSnooze();
        writeAsync();
    }

    public long getPolicyWarningBytes(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        if (policy != null) {
            return policy.warningBytes;
        }
        return -1L;
    }

    private void setPolicyWarningBytesInner(NetworkTemplate template, long warningBytes) {
        NetworkPolicy policy = getOrCreatePolicy(template);
        policy.warningBytes = warningBytes;
        policy.inferred = false;
        policy.clearSnooze();
        writeAsync();
    }

    public void setPolicyWarningBytes(NetworkTemplate template, long warningBytes) {
        long limitBytes = getPolicyLimitBytes(template);
        setPolicyWarningBytesInner(template, limitBytes == -1 ? warningBytes : Math.min(warningBytes, limitBytes));
    }

    public long getPolicyLimitBytes(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        if (policy != null) {
            return policy.limitBytes;
        }
        return -1L;
    }

    public void setPolicyLimitBytes(NetworkTemplate template, long limitBytes) {
        long warningBytes = getPolicyWarningBytes(template);
        if (warningBytes > limitBytes && limitBytes != -1) {
            setPolicyWarningBytesInner(template, limitBytes);
        }
        NetworkPolicy policy = getOrCreatePolicy(template);
        policy.limitBytes = limitBytes;
        policy.inferred = false;
        policy.clearSnooze();
        writeAsync();
    }

    private static NetworkTemplate buildUnquotedNetworkTemplate(NetworkTemplate template) {
        if (template == null) {
            return null;
        }
        String networkId = template.getNetworkId();
        String strippedNetworkId = WifiInfo.removeDoubleQuotes(networkId);
        if (TextUtils.equals(strippedNetworkId, networkId)) {
            return null;
        }
        return new NetworkTemplate(template.getMatchRule(), template.getSubscriberId(), strippedNetworkId);
    }
}
