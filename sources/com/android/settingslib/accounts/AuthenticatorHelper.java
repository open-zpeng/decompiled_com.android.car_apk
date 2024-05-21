package com.android.settingslib.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
/* loaded from: classes3.dex */
public final class AuthenticatorHelper extends BroadcastReceiver {
    private static final String TAG = "AuthenticatorHelper";
    private final Context mContext;
    private final OnAccountsUpdateListener mListener;
    private boolean mListeningToAccountUpdates;
    private final UserHandle mUserHandle;
    private final Map<String, AuthenticatorDescription> mTypeToAuthDescription = new HashMap();
    private final ArrayList<String> mEnabledAccountTypes = new ArrayList<>();
    private final Map<String, Drawable> mAccTypeIconCache = new HashMap();
    private final HashMap<String, ArrayList<String>> mAccountTypeToAuthorities = new HashMap<>();

    /* loaded from: classes3.dex */
    public interface OnAccountsUpdateListener {
        void onAccountsUpdate(UserHandle userHandle);
    }

    public AuthenticatorHelper(Context context, UserHandle userHandle, OnAccountsUpdateListener listener) {
        this.mContext = context;
        this.mUserHandle = userHandle;
        this.mListener = listener;
        onAccountsUpdated(null);
    }

    public String[] getEnabledAccountTypes() {
        ArrayList<String> arrayList = this.mEnabledAccountTypes;
        return (String[]) arrayList.toArray(new String[arrayList.size()]);
    }

    /* JADX WARN: Type inference failed for: r0v0, types: [com.android.settingslib.accounts.AuthenticatorHelper$1] */
    public void preloadDrawableForType(final Context context, final String accountType) {
        new AsyncTask<Void, Void, Void>() { // from class: com.android.settingslib.accounts.AuthenticatorHelper.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // android.os.AsyncTask
            public Void doInBackground(Void... params) {
                AuthenticatorHelper.this.getDrawableForType(context, accountType);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
    }

    public Drawable getDrawableForType(Context context, String accountType) {
        Drawable icon = null;
        synchronized (this.mAccTypeIconCache) {
            if (this.mAccTypeIconCache.containsKey(accountType)) {
                return this.mAccTypeIconCache.get(accountType);
            }
            if (this.mTypeToAuthDescription.containsKey(accountType)) {
                try {
                    AuthenticatorDescription desc = this.mTypeToAuthDescription.get(accountType);
                    Context authContext = context.createPackageContextAsUser(desc.packageName, 0, this.mUserHandle);
                    icon = this.mContext.getPackageManager().getUserBadgedIcon(authContext.getDrawable(desc.iconId), this.mUserHandle);
                    synchronized (this.mAccTypeIconCache) {
                        this.mAccTypeIconCache.put(accountType, icon);
                    }
                } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                }
            }
            if (icon == null) {
                return context.getPackageManager().getDefaultActivityIcon();
            }
            return icon;
        }
    }

    public CharSequence getLabelForType(Context context, String accountType) {
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            try {
                AuthenticatorDescription desc = this.mTypeToAuthDescription.get(accountType);
                Context authContext = context.createPackageContextAsUser(desc.packageName, 0, this.mUserHandle);
                CharSequence label = authContext.getResources().getText(desc.labelId);
                return label;
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "No label name for account type " + accountType);
                return null;
            } catch (Resources.NotFoundException e2) {
                Log.w(TAG, "No label icon for account type " + accountType);
                return null;
            }
        }
        return null;
    }

    public String getPackageForType(String accountType) {
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            AuthenticatorDescription desc = this.mTypeToAuthDescription.get(accountType);
            return desc.packageName;
        }
        return null;
    }

    public int getLabelIdForType(String accountType) {
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            AuthenticatorDescription desc = this.mTypeToAuthDescription.get(accountType);
            return desc.labelId;
        }
        return -1;
    }

    public void updateAuthDescriptions(Context context) {
        AuthenticatorDescription[] authDescs = AccountManager.get(context).getAuthenticatorTypesAsUser(this.mUserHandle.getIdentifier());
        for (int i = 0; i < authDescs.length; i++) {
            this.mTypeToAuthDescription.put(authDescs[i].type, authDescs[i]);
        }
    }

    public boolean containsAccountType(String accountType) {
        return this.mTypeToAuthDescription.containsKey(accountType);
    }

    public AuthenticatorDescription getAccountTypeDescription(String accountType) {
        return this.mTypeToAuthDescription.get(accountType);
    }

    public boolean hasAccountPreferences(String accountType) {
        AuthenticatorDescription desc;
        if (containsAccountType(accountType) && (desc = getAccountTypeDescription(accountType)) != null && desc.accountPreferencesId != 0) {
            return true;
        }
        return false;
    }

    void onAccountsUpdated(Account[] accounts) {
        updateAuthDescriptions(this.mContext);
        if (accounts == null) {
            accounts = AccountManager.get(this.mContext).getAccountsAsUser(this.mUserHandle.getIdentifier());
        }
        this.mEnabledAccountTypes.clear();
        this.mAccTypeIconCache.clear();
        for (Account account : accounts) {
            if (!this.mEnabledAccountTypes.contains(account.type)) {
                this.mEnabledAccountTypes.add(account.type);
            }
        }
        buildAccountTypeToAuthoritiesMap();
        if (this.mListeningToAccountUpdates) {
            this.mListener.onAccountsUpdate(this.mUserHandle);
        }
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        Account[] accounts = AccountManager.get(this.mContext).getAccountsAsUser(this.mUserHandle.getIdentifier());
        onAccountsUpdated(accounts);
    }

    public void listenToAccountUpdates() {
        if (!this.mListeningToAccountUpdates) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.accounts.LOGIN_ACCOUNTS_CHANGED");
            intentFilter.addAction("android.intent.action.DEVICE_STORAGE_OK");
            this.mContext.registerReceiverAsUser(this, this.mUserHandle, intentFilter, null, null);
            this.mListeningToAccountUpdates = true;
        }
    }

    public void stopListeningToAccountUpdates() {
        if (this.mListeningToAccountUpdates) {
            this.mContext.unregisterReceiver(this);
            this.mListeningToAccountUpdates = false;
        }
    }

    public ArrayList<String> getAuthoritiesForAccountType(String type) {
        return this.mAccountTypeToAuthorities.get(type);
    }

    private void buildAccountTypeToAuthoritiesMap() {
        this.mAccountTypeToAuthorities.clear();
        SyncAdapterType[] syncAdapters = ContentResolver.getSyncAdapterTypesAsUser(this.mUserHandle.getIdentifier());
        for (SyncAdapterType sa : syncAdapters) {
            ArrayList<String> authorities = this.mAccountTypeToAuthorities.get(sa.accountType);
            if (authorities == null) {
                authorities = new ArrayList<>();
                this.mAccountTypeToAuthorities.put(sa.accountType, authorities);
            }
            if (Log.isLoggable(TAG, 2)) {
                Log.v(TAG, "Added authority " + sa.authority + " to accountType " + sa.accountType);
            }
            authorities.add(sa.authority);
        }
    }
}
