package com.android.car.audio;

import android.content.pm.PackageManager;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.os.Bundle;
import android.util.Slog;
import com.android.car.Manifest;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
/* loaded from: classes3.dex */
public class CarAudioFocus extends AudioPolicy.AudioPolicyFocusListener {
    static final /* synthetic */ boolean $assertionsDisabled = false;
    static final int INTERACTION_CONCURRENT = 2;
    static final int INTERACTION_EXCLUSIVE = 1;
    static final int INTERACTION_REJECT = 0;
    private static final String TAG = "CarAudioFocus";
    private static int[][] sInteractionMatrix = {new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0}, new int[]{0, 1, 2, 1, 1, 1, 1, 2, 2}, new int[]{0, 2, 2, 1, 2, 1, 2, 2, 2}, new int[]{0, 2, 0, 2, 1, 1, 0, 0, 0}, new int[]{0, 0, 2, 2, 2, 2, 0, 0, 2}, new int[]{0, 0, 2, 0, 2, 2, 2, 2, 0}, new int[]{0, 2, 2, 1, 1, 1, 2, 2, 2}, new int[]{0, 2, 2, 1, 1, 1, 2, 2, 2}, new int[]{0, 2, 2, 1, 1, 1, 2, 2, 2}};
    private final AudioManager mAudioManager;
    private AudioPolicy mAudioPolicy;
    private CarAudioService mCarAudioService;
    private final HashMap<String, FocusEntry> mFocusHolders = new HashMap<>();
    private final HashMap<String, FocusEntry> mFocusLosers = new HashMap<>();
    private final PackageManager mPackageManager;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class FocusEntry {
        final AudioFocusInfo mAfi;
        final int mAudioContext;
        final ArrayList<FocusEntry> mBlockers = new ArrayList<>();
        boolean mReceivedLossTransientCanDuck;

        FocusEntry(AudioFocusInfo afi, int context) {
            this.mAfi = afi;
            this.mAudioContext = context;
        }

        public String getClientId() {
            return this.mAfi.getClientId();
        }

        public boolean wantsPauseInsteadOfDucking() {
            return (this.mAfi.getFlags() & 2) != 0;
        }

        public boolean receivesDuckEvents() {
            Bundle bundle = this.mAfi.getAttributes().getBundle();
            return bundle != null && bundle.getBoolean("android.car.media.AUDIOFOCUS_EXTRA_RECEIVE_DUCKING_EVENTS") && CarAudioFocus.this.mPackageManager.checkPermission(Manifest.permission.RECEIVE_CAR_AUDIO_DUCKING_EVENTS, this.mAfi.getPackageName()) == 0;
        }

        String getUsageName() {
            return this.mAfi.getAttributes().usageToString();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioFocus(AudioManager audioManager, PackageManager packageManager) {
        this.mAudioManager = audioManager;
        this.mPackageManager = packageManager;
    }

    public void setOwningPolicy(CarAudioService audioService, AudioPolicy parentPolicy) {
        this.mCarAudioService = audioService;
        this.mAudioPolicy = parentPolicy;
    }

    private void sendFocusLoss(FocusEntry loser, int lossType) {
        Slog.i(TAG, "sendFocusLoss (" + focusEventToString(lossType) + ") to " + loser.getClientId());
        int result = this.mAudioManager.dispatchAudioFocusChange(loser.mAfi, lossType, this.mAudioPolicy);
        if (result != 1) {
            Slog.e(TAG, "Failure to signal loss of audio focus with error: " + result);
        }
    }

    int evaluateFocusRequest(AudioFocusInfo afi) {
        int lossType;
        ArrayList<FocusEntry> losers;
        Slog.i(TAG, "Evaluating " + focusEventToString(afi.getGainRequest()) + " request for client " + afi.getClientId() + " with usage " + afi.getAttributes().usageToString());
        boolean permanent = afi.getGainRequest() == 1;
        boolean allowDucking = afi.getGainRequest() == 3;
        int requestedContext = this.mCarAudioService.getContextForUsage(afi.getAttributes().getUsage());
        FocusEntry replacedCurrentEntry = null;
        FocusEntry replacedBlockedEntry = null;
        Slog.i(TAG, "Scanning focus holders...");
        ArrayList<FocusEntry> losers2 = new ArrayList<>();
        for (FocusEntry entry : this.mFocusHolders.values()) {
            StringBuilder sb = new StringBuilder();
            FocusEntry replacedBlockedEntry2 = replacedBlockedEntry;
            sb.append("Evaluating focus holder: ");
            sb.append(entry.getClientId());
            Slog.d(TAG, sb.toString());
            if (requestedContext == 7 && entry.mAfi.getGainRequest() == 4) {
                return 0;
            }
            if (afi.getClientId().equals(entry.mAfi.getClientId())) {
                if (entry.mAudioContext == requestedContext) {
                    Slog.i(TAG, "Replacing accepted request from same client");
                    replacedCurrentEntry = entry;
                    replacedBlockedEntry = replacedBlockedEntry2;
                } else {
                    Slog.e(TAG, "Client " + entry.getClientId() + " has already requested focus for " + entry.mAfi.getAttributes().usageToString() + " - cannot request focus for " + afi.getAttributes().usageToString() + " on same listener.");
                    return 0;
                }
            } else {
                int i = sInteractionMatrix[entry.mAudioContext][requestedContext];
                if (i == 0) {
                    return 0;
                }
                if (i == 1) {
                    losers2.add(entry);
                } else if (i == 2) {
                    if (!allowDucking || entry.wantsPauseInsteadOfDucking() || entry.receivesDuckEvents()) {
                        losers2.add(entry);
                    }
                } else {
                    Slog.e(TAG, "Bad interaction matrix value - rejecting");
                    return 0;
                }
                replacedBlockedEntry = replacedBlockedEntry2;
            }
        }
        FocusEntry replacedBlockedEntry3 = replacedBlockedEntry;
        Slog.i(TAG, "Scanning those who've already lost focus...");
        ArrayList<FocusEntry> blocked = new ArrayList<>();
        Iterator<FocusEntry> it = this.mFocusLosers.values().iterator();
        FocusEntry replacedBlockedEntry4 = replacedBlockedEntry3;
        while (it.hasNext()) {
            FocusEntry entry2 = it.next();
            Iterator<FocusEntry> it2 = it;
            Slog.i(TAG, entry2.mAfi.getClientId());
            if (requestedContext != 7) {
                losers = losers2;
            } else {
                losers = losers2;
                if (entry2.mAfi.getGainRequest() == 4) {
                    return 0;
                }
            }
            if (afi.getClientId().equals(entry2.mAfi.getClientId())) {
                if (entry2.mAudioContext == requestedContext) {
                    Slog.i(TAG, "Replacing pending request from same client");
                    replacedBlockedEntry4 = entry2;
                    it = it2;
                    losers2 = losers;
                } else {
                    Slog.e(TAG, "Client " + entry2.getClientId() + " has already requested focus for " + entry2.mAfi.getAttributes().usageToString() + " - cannot request focus for " + afi.getAttributes().usageToString() + " on same listener.");
                    return 0;
                }
            } else {
                int i2 = sInteractionMatrix[entry2.mAudioContext][requestedContext];
                if (i2 == 0) {
                    return 0;
                }
                if (i2 == 1) {
                    blocked.add(entry2);
                } else if (i2 == 2) {
                    if (!allowDucking || entry2.wantsPauseInsteadOfDucking() || entry2.receivesDuckEvents()) {
                        blocked.add(entry2);
                    }
                } else {
                    Slog.e(TAG, "Bad interaction matrix value - rejecting");
                    return 0;
                }
                it = it2;
                losers2 = losers;
            }
        }
        ArrayList<FocusEntry> losers3 = losers2;
        FocusEntry newEntry = new FocusEntry(afi, requestedContext);
        ArrayList<FocusEntry> permanentlyLost = new ArrayList<>();
        if (replacedCurrentEntry != null) {
            this.mFocusHolders.remove(replacedCurrentEntry.getClientId());
            permanentlyLost.add(replacedCurrentEntry);
        }
        if (replacedBlockedEntry4 != null) {
            this.mFocusLosers.remove(replacedBlockedEntry4.getClientId());
            permanentlyLost.add(replacedBlockedEntry4);
        }
        Iterator<FocusEntry> it3 = blocked.iterator();
        while (it3.hasNext()) {
            FocusEntry entry3 = it3.next();
            if (permanent) {
                sendFocusLoss(entry3, -1);
                entry3.mReceivedLossTransientCanDuck = false;
                this.mFocusLosers.remove(entry3.mAfi.getClientId());
                permanentlyLost.add(entry3);
            } else {
                if (!allowDucking && entry3.mReceivedLossTransientCanDuck) {
                    Slog.i(TAG, "Converting duckable loss to non-duckable for " + entry3.getClientId());
                    sendFocusLoss(entry3, -2);
                    entry3.mReceivedLossTransientCanDuck = false;
                }
                entry3.mBlockers.add(newEntry);
            }
        }
        Iterator<FocusEntry> it4 = losers3.iterator();
        while (it4.hasNext()) {
            FocusEntry entry4 = it4.next();
            if (permanent) {
                lossType = -1;
            } else if (allowDucking && entry4.receivesDuckEvents()) {
                lossType = -3;
                entry4.mReceivedLossTransientCanDuck = true;
            } else {
                lossType = -2;
            }
            sendFocusLoss(entry4, lossType);
            this.mFocusHolders.remove(entry4.mAfi.getClientId());
            if (permanent) {
                permanentlyLost.add(entry4);
            } else {
                this.mFocusLosers.put(entry4.mAfi.getClientId(), entry4);
                entry4.mBlockers.add(newEntry);
            }
        }
        Iterator<FocusEntry> it5 = permanentlyLost.iterator();
        while (it5.hasNext()) {
            FocusEntry entry5 = it5.next();
            Slog.d(TAG, "Cleaning up entry " + entry5.getClientId());
            removeFocusEntryAndRestoreUnblockedWaiters(entry5);
        }
        this.mFocusHolders.put(afi.getClientId(), newEntry);
        Slog.i(TAG, "AUDIOFOCUS_REQUEST_GRANTED");
        return 1;
    }

    public synchronized void onAudioFocusRequest(AudioFocusInfo afi, int requestResult) {
        Slog.i(TAG, "onAudioFocusRequest " + afi.getClientId());
        int response = evaluateFocusRequest(afi);
        this.mAudioManager.setFocusRequestResult(afi, response, this.mAudioPolicy);
    }

    public synchronized void onAudioFocusAbandon(AudioFocusInfo afi) {
        Slog.i(TAG, "onAudioFocusAbandon " + afi.getClientId());
        FocusEntry deadEntry = removeFocusEntry(afi);
        if (deadEntry != null) {
            removeFocusEntryAndRestoreUnblockedWaiters(deadEntry);
        }
    }

    private FocusEntry removeFocusEntry(AudioFocusInfo afi) {
        Slog.i(TAG, "removeFocusEntry " + afi.getClientId());
        FocusEntry deadEntry = this.mFocusHolders.remove(afi.getClientId());
        if (deadEntry == null && (deadEntry = this.mFocusLosers.remove(afi.getClientId())) == null) {
            Slog.w(TAG, "Audio focus abandoned by unrecognized client id: " + afi.getClientId());
        }
        return deadEntry;
    }

    private void removeFocusEntryAndRestoreUnblockedWaiters(FocusEntry deadEntry) {
        Iterator<FocusEntry> it = this.mFocusLosers.values().iterator();
        while (it.hasNext()) {
            FocusEntry entry = it.next();
            entry.mBlockers.remove(deadEntry);
            if (entry.mBlockers.isEmpty()) {
                Slog.i(TAG, "Restoring unblocked entry " + entry.getClientId());
                it.remove();
                this.mFocusHolders.put(entry.getClientId(), entry);
                dispatchFocusGained(entry.mAfi);
            }
        }
    }

    private int dispatchFocusGained(AudioFocusInfo afi) {
        int result = this.mAudioManager.dispatchAudioFocusChange(afi, afi.getGainRequest(), this.mAudioPolicy);
        if (result != 1) {
            Slog.e(TAG, "Failure to signal gain of audio focus with error: " + result);
        }
        return result;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public ArrayList<AudioFocusInfo> getAudioFocusLosersForUid(int uid) {
        return getAudioFocusListForUid(uid, this.mFocusLosers);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public ArrayList<AudioFocusInfo> getAudioFocusHoldersForUid(int uid) {
        return getAudioFocusListForUid(uid, this.mFocusHolders);
    }

    private ArrayList<AudioFocusInfo> getAudioFocusListForUid(int uid, HashMap<String, FocusEntry> mapToQuery) {
        ArrayList<AudioFocusInfo> matchingInfoList = new ArrayList<>();
        for (String clientId : mapToQuery.keySet()) {
            AudioFocusInfo afi = mapToQuery.get(clientId).mAfi;
            if (afi.getClientUid() == uid) {
                matchingInfoList.add(afi);
            }
        }
        return matchingInfoList;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void removeAudioFocusInfoAndTransientlyLoseFocus(AudioFocusInfo afi) {
        FocusEntry deadEntry = removeFocusEntry(afi);
        if (deadEntry != null) {
            sendFocusLoss(deadEntry, -2);
            removeFocusEntryAndRestoreUnblockedWaiters(deadEntry);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int reevaluateAndRegainAudioFocus(AudioFocusInfo afi) {
        int results = evaluateFocusRequest(afi);
        if (results == 1) {
            return dispatchFocusGained(afi);
        }
        return results;
    }

    public synchronized void dump(String indent, PrintWriter writer) {
        writer.printf("%s*CarAudioFocus*\n", indent);
        String innerIndent = indent + "\t";
        writer.printf("%sCurrent Focus Holders:\n", innerIndent);
        for (String clientId : this.mFocusHolders.keySet()) {
            writer.printf("%s\t%s - %s\n", innerIndent, clientId, this.mFocusHolders.get(clientId).getUsageName());
        }
        writer.printf("%sTransient Focus Losers:\n", innerIndent);
        for (String clientId2 : this.mFocusLosers.keySet()) {
            writer.printf("%s\t%s - %s\n", innerIndent, clientId2, this.mFocusLosers.get(clientId2).getUsageName());
        }
    }

    private static String focusEventToString(int focusEvent) {
        if (focusEvent != -3) {
            if (focusEvent != -2) {
                if (focusEvent != -1) {
                    if (focusEvent != 1) {
                        if (focusEvent != 2) {
                            if (focusEvent != 3) {
                                if (focusEvent == 4) {
                                    return "GAIN_TRANSIENT_EXCLUSIVE";
                                }
                                return "unknown event " + focusEvent;
                            }
                            return "GAIN_TRANSIENT_MAY_DUCK";
                        }
                        return "GAIN_TRANSIENT";
                    }
                    return "GAIN";
                }
                return "LOSS";
            }
            return "LOSS_TRANSIENT";
        }
        return "LOSS_TRANSIENT_CAN_DUCK";
    }
}
