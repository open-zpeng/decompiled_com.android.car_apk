package com.android.settingslib.widget;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
/* loaded from: classes3.dex */
public class FooterPreference extends Preference {
    public static final String KEY_FOOTER = "footer_preference";
    static final int ORDER_FOOTER = 2147483646;

    public FooterPreference(Context context, AttributeSet attrs) {
        super(context, attrs, TypedArrayUtils.getAttr(context, com.android.settingslib.R.attr.footerPreferenceStyle, 16842894));
        init();
    }

    public FooterPreference(Context context) {
        this(context, null);
    }

    @Override // androidx.preference.Preference
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView title = (TextView) holder.itemView.findViewById(16908310);
        title.setMovementMethod(new LinkMovementMethod());
        title.setClickable(false);
        title.setLongClickable(false);
    }

    private void init() {
        setIcon(com.android.settingslib.R.drawable.ic_info_outline_24);
        setKey(KEY_FOOTER);
        setOrder(ORDER_FOOTER);
    }
}
