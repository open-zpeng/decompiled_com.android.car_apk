package com.android.settingslib.wifi;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiConfiguration;
import android.os.Looper;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.android.settingslib.R;
import com.android.settingslib.TronUtils;
import com.android.settingslib.Utils;
/* loaded from: classes3.dex */
public class AccessPointPreference extends Preference {
    private AccessPoint mAccessPoint;
    private Drawable mBadge;
    private final UserBadgeCache mBadgeCache;
    private final int mBadgePadding;
    private CharSequence mContentDescription;
    private int mDefaultIconResId;
    private boolean mForSavedNetworks;
    private final StateListDrawable mFrictionSld;
    private final IconInjector mIconInjector;
    private int mLevel;
    private final Runnable mNotifyChanged;
    private boolean mShowDivider;
    private TextView mTitleView;
    private int mWifiSpeed;
    private static final int[] STATE_SECURED = {R.attr.state_encrypted};
    private static final int[] STATE_METERED = {R.attr.state_metered};
    private static final int[] FRICTION_ATTRS = {R.attr.wifi_friction};
    private static final int[] WIFI_CONNECTION_STRENGTH = {R.string.accessibility_no_wifi, R.string.accessibility_wifi_one_bar, R.string.accessibility_wifi_two_bars, R.string.accessibility_wifi_three_bars, R.string.accessibility_wifi_signal_full};

    private static StateListDrawable getFrictionStateListDrawable(Context context) {
        TypedArray frictionSld;
        try {
            frictionSld = context.getTheme().obtainStyledAttributes(FRICTION_ATTRS);
        } catch (Resources.NotFoundException e) {
            frictionSld = null;
        }
        if (frictionSld != null) {
            return (StateListDrawable) frictionSld.getDrawable(0);
        }
        return null;
    }

    public AccessPointPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mForSavedNetworks = false;
        this.mWifiSpeed = 0;
        this.mNotifyChanged = new Runnable() { // from class: com.android.settingslib.wifi.AccessPointPreference.1
            @Override // java.lang.Runnable
            public void run() {
                AccessPointPreference.this.notifyChanged();
            }
        };
        this.mFrictionSld = null;
        this.mBadgePadding = 0;
        this.mBadgeCache = null;
        this.mIconInjector = new IconInjector(context);
    }

    public AccessPointPreference(AccessPoint accessPoint, Context context, UserBadgeCache cache, boolean forSavedNetworks) {
        this(accessPoint, context, cache, 0, forSavedNetworks);
        refresh();
    }

    public AccessPointPreference(AccessPoint accessPoint, Context context, UserBadgeCache cache, int iconResId, boolean forSavedNetworks) {
        this(accessPoint, context, cache, iconResId, forSavedNetworks, getFrictionStateListDrawable(context), -1, new IconInjector(context));
    }

    @VisibleForTesting
    AccessPointPreference(AccessPoint accessPoint, Context context, UserBadgeCache cache, int iconResId, boolean forSavedNetworks, StateListDrawable frictionSld, int level, IconInjector iconInjector) {
        super(context);
        this.mForSavedNetworks = false;
        this.mWifiSpeed = 0;
        this.mNotifyChanged = new Runnable() { // from class: com.android.settingslib.wifi.AccessPointPreference.1
            @Override // java.lang.Runnable
            public void run() {
                AccessPointPreference.this.notifyChanged();
            }
        };
        setLayoutResource(R.layout.preference_access_point);
        setWidgetLayoutResource(getWidgetLayoutResourceId());
        this.mBadgeCache = cache;
        this.mAccessPoint = accessPoint;
        this.mForSavedNetworks = forSavedNetworks;
        this.mAccessPoint.setTag(this);
        this.mLevel = level;
        this.mDefaultIconResId = iconResId;
        this.mFrictionSld = frictionSld;
        this.mIconInjector = iconInjector;
        this.mBadgePadding = context.getResources().getDimensionPixelSize(R.dimen.wifi_preference_badge_padding);
    }

    protected int getWidgetLayoutResourceId() {
        return R.layout.access_point_friction_widget;
    }

    public AccessPoint getAccessPoint() {
        return this.mAccessPoint;
    }

    @Override // androidx.preference.Preference
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        if (this.mAccessPoint == null) {
            return;
        }
        Drawable drawable = getIcon();
        if (drawable != null) {
            drawable.setLevel(this.mLevel);
        }
        this.mTitleView = (TextView) view.findViewById(16908310);
        TextView textView = this.mTitleView;
        if (textView != null) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds((Drawable) null, (Drawable) null, this.mBadge, (Drawable) null);
            this.mTitleView.setCompoundDrawablePadding(this.mBadgePadding);
        }
        view.itemView.setContentDescription(this.mContentDescription);
        ImageView frictionImageView = (ImageView) view.findViewById(R.id.friction_icon);
        bindFrictionImage(frictionImageView);
        View divider = view.findViewById(R.id.two_target_divider);
        divider.setVisibility(shouldShowDivider() ? 0 : 4);
    }

    public boolean shouldShowDivider() {
        return this.mShowDivider;
    }

    public void setShowDivider(boolean showDivider) {
        this.mShowDivider = showDivider;
        notifyChanged();
    }

    protected void updateIcon(int level, Context context) {
        if (level == -1) {
            safeSetDefaultIcon();
            return;
        }
        TronUtils.logWifiSettingsSpeed(context, this.mWifiSpeed);
        Drawable drawable = this.mIconInjector.getIcon(level);
        if (!this.mForSavedNetworks && drawable != null) {
            drawable.setTintList(Utils.getColorAttr(context, 16843817));
            setIcon(drawable);
            return;
        }
        safeSetDefaultIcon();
    }

    private void bindFrictionImage(ImageView frictionImageView) {
        if (frictionImageView == null || this.mFrictionSld == null) {
            return;
        }
        if (this.mAccessPoint.getSecurity() != 0 && this.mAccessPoint.getSecurity() != 4) {
            this.mFrictionSld.setState(STATE_SECURED);
        } else if (this.mAccessPoint.isMetered()) {
            this.mFrictionSld.setState(STATE_METERED);
        }
        Drawable drawable = this.mFrictionSld.getCurrent();
        frictionImageView.setImageDrawable(drawable);
    }

    private void safeSetDefaultIcon() {
        int i = this.mDefaultIconResId;
        if (i != 0) {
            setIcon(i);
        } else {
            setIcon((Drawable) null);
        }
    }

    protected void updateBadge(Context context) {
        WifiConfiguration config = this.mAccessPoint.getConfig();
        if (config == null) {
            return;
        }
        this.mBadge = this.mBadgeCache.getUserBadge(config.creatorUid);
    }

    public void refresh() {
        setTitle(this, this.mAccessPoint);
        Context context = getContext();
        int level = this.mAccessPoint.getLevel();
        int wifiSpeed = this.mAccessPoint.getSpeed();
        if (level != this.mLevel || wifiSpeed != this.mWifiSpeed) {
            this.mLevel = level;
            this.mWifiSpeed = wifiSpeed;
            updateIcon(this.mLevel, context);
            notifyChanged();
        }
        updateBadge(context);
        setSummary(this.mForSavedNetworks ? this.mAccessPoint.getSavedNetworkSummary() : this.mAccessPoint.getSettingsSummary());
        this.mContentDescription = buildContentDescription(getContext(), this, this.mAccessPoint);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.preference.Preference
    public void notifyChanged() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            postNotifyChanged();
        } else {
            super.notifyChanged();
        }
    }

    @VisibleForTesting
    static void setTitle(AccessPointPreference preference, AccessPoint ap) {
        preference.setTitle(ap.getTitle());
    }

    @VisibleForTesting
    static CharSequence buildContentDescription(Context context, Preference pref, AccessPoint ap) {
        String string;
        CharSequence contentDescription = pref.getTitle();
        CharSequence summary = pref.getSummary();
        if (!TextUtils.isEmpty(summary)) {
            contentDescription = TextUtils.concat(contentDescription, ",", summary);
        }
        int level = ap.getLevel();
        if (level >= 0) {
            int[] iArr = WIFI_CONNECTION_STRENGTH;
            if (level < iArr.length) {
                contentDescription = TextUtils.concat(contentDescription, ",", context.getString(iArr[level]));
            }
        }
        CharSequence[] charSequenceArr = new CharSequence[3];
        charSequenceArr[0] = contentDescription;
        charSequenceArr[1] = ",";
        if (ap.getSecurity() == 0) {
            string = context.getString(R.string.accessibility_wifi_security_type_none);
        } else {
            string = context.getString(R.string.accessibility_wifi_security_type_secured);
        }
        charSequenceArr[2] = string;
        return TextUtils.concat(charSequenceArr);
    }

    public void onLevelChanged() {
        postNotifyChanged();
    }

    private void postNotifyChanged() {
        TextView textView = this.mTitleView;
        if (textView != null) {
            textView.post(this.mNotifyChanged);
        }
    }

    /* loaded from: classes3.dex */
    public static class UserBadgeCache {
        private final SparseArray<Drawable> mBadges = new SparseArray<>();
        private final PackageManager mPm;

        public UserBadgeCache(PackageManager pm) {
            this.mPm = pm;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public Drawable getUserBadge(int userId) {
            int index = this.mBadges.indexOfKey(userId);
            if (index < 0) {
                Drawable badge = this.mPm.getUserBadgeForDensity(new UserHandle(userId), 0);
                this.mBadges.put(userId, badge);
                return badge;
            }
            return this.mBadges.valueAt(index);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static class IconInjector {
        private final Context mContext;

        public IconInjector(Context context) {
            this.mContext = context;
        }

        public Drawable getIcon(int level) {
            return this.mContext.getDrawable(Utils.getWifiIconResource(level));
        }
    }
}
