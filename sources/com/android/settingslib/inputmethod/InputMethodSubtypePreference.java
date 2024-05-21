package com.android.settingslib.inputmethod;

import android.content.Context;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import androidx.preference.Preference;
import com.android.internal.annotations.VisibleForTesting;
import java.text.Collator;
import java.util.Locale;
/* loaded from: classes3.dex */
public class InputMethodSubtypePreference extends SwitchWithNoTextPreference {
    private final boolean mIsSystemLanguage;
    private final boolean mIsSystemLocale;

    public InputMethodSubtypePreference(Context context, InputMethodSubtype subtype, InputMethodInfo imi) {
        this(context, imi.getId() + subtype.hashCode(), InputMethodAndSubtypeUtil.getSubtypeLocaleNameAsSentence(subtype, context, imi), subtype.getLocaleObject(), context.getResources().getConfiguration().locale);
    }

    @VisibleForTesting
    InputMethodSubtypePreference(Context context, String prefKey, CharSequence title, Locale subtypeLocale, Locale systemLocale) {
        super(context);
        boolean z = false;
        setPersistent(false);
        setKey(prefKey);
        setTitle(title);
        if (subtypeLocale == null) {
            this.mIsSystemLocale = false;
            this.mIsSystemLanguage = false;
            return;
        }
        this.mIsSystemLocale = subtypeLocale.equals(systemLocale);
        this.mIsSystemLanguage = (this.mIsSystemLocale || TextUtils.equals(subtypeLocale.getLanguage(), systemLocale.getLanguage())) ? true : true;
    }

    public int compareTo(Preference rhs, Collator collator) {
        if (this == rhs) {
            return 0;
        }
        if (rhs instanceof InputMethodSubtypePreference) {
            InputMethodSubtypePreference rhsPref = (InputMethodSubtypePreference) rhs;
            if (!this.mIsSystemLocale || rhsPref.mIsSystemLocale) {
                if (this.mIsSystemLocale || !rhsPref.mIsSystemLocale) {
                    if (!this.mIsSystemLanguage || rhsPref.mIsSystemLanguage) {
                        if (this.mIsSystemLanguage || !rhsPref.mIsSystemLanguage) {
                            CharSequence title = getTitle();
                            CharSequence rhsTitle = rhs.getTitle();
                            boolean emptyTitle = TextUtils.isEmpty(title);
                            boolean rhsEmptyTitle = TextUtils.isEmpty(rhsTitle);
                            if (emptyTitle || rhsEmptyTitle) {
                                return (emptyTitle ? -1 : 0) - (rhsEmptyTitle ? -1 : 0);
                            }
                            return collator.compare(title.toString(), rhsTitle.toString());
                        }
                        return 1;
                    }
                    return -1;
                }
                return 1;
            }
            return -1;
        }
        return super.compareTo(rhs);
    }
}
