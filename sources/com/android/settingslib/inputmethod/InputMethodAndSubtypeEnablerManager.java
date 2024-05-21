package com.android.settingslib.inputmethod;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import com.android.settingslib.R;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes3.dex */
public class InputMethodAndSubtypeEnablerManager implements Preference.OnPreferenceChangeListener {
    private final PreferenceFragment mFragment;
    private boolean mHaveHardKeyboard;
    private InputMethodManager mImm;
    private List<InputMethodInfo> mInputMethodInfoList;
    private final HashMap<String, List<Preference>> mInputMethodAndSubtypePrefsMap = new HashMap<>();
    private final HashMap<String, TwoStatePreference> mAutoSelectionPrefsMap = new HashMap<>();
    private final Collator mCollator = Collator.getInstance();

    public InputMethodAndSubtypeEnablerManager(PreferenceFragment fragment) {
        this.mFragment = fragment;
        this.mImm = (InputMethodManager) fragment.getContext().getSystemService(InputMethodManager.class);
        this.mInputMethodInfoList = this.mImm.getInputMethodList();
    }

    public void init(PreferenceFragment fragment, String targetImi, PreferenceScreen root) {
        Configuration config = fragment.getResources().getConfiguration();
        this.mHaveHardKeyboard = config.keyboard == 2;
        for (InputMethodInfo imi : this.mInputMethodInfoList) {
            if (imi.getId().equals(targetImi) || TextUtils.isEmpty(targetImi)) {
                addInputMethodSubtypePreferences(fragment, imi, root);
            }
        }
    }

    public void refresh(Context context, PreferenceFragment fragment) {
        InputMethodSettingValuesWrapper.getInstance(context).refreshAllInputMethodAndSubtypes();
        InputMethodAndSubtypeUtil.loadInputMethodSubtypeList(fragment, context.getContentResolver(), this.mInputMethodInfoList, this.mInputMethodAndSubtypePrefsMap);
        updateAutoSelectionPreferences();
    }

    public void save(Context context, PreferenceFragment fragment) {
        InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(fragment, context.getContentResolver(), this.mInputMethodInfoList, this.mHaveHardKeyboard);
    }

    @Override // androidx.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (newValue instanceof Boolean) {
            boolean isChecking = ((Boolean) newValue).booleanValue();
            for (String imiId : this.mAutoSelectionPrefsMap.keySet()) {
                if (this.mAutoSelectionPrefsMap.get(imiId) == pref) {
                    TwoStatePreference autoSelectionPref = (TwoStatePreference) pref;
                    autoSelectionPref.setChecked(isChecking);
                    setAutoSelectionSubtypesEnabled(imiId, autoSelectionPref.isChecked());
                    return false;
                }
            }
            if (pref instanceof InputMethodSubtypePreference) {
                InputMethodSubtypePreference subtypePref = (InputMethodSubtypePreference) pref;
                subtypePref.setChecked(isChecking);
                if (!subtypePref.isChecked()) {
                    updateAutoSelectionPreferences();
                }
                return false;
            }
            return true;
        }
        return true;
    }

    private void addInputMethodSubtypePreferences(PreferenceFragment fragment, InputMethodInfo imi, PreferenceScreen root) {
        Context prefContext = fragment.getPreferenceManager().getContext();
        int subtypeCount = imi.getSubtypeCount();
        if (subtypeCount <= 1) {
            return;
        }
        String imiId = imi.getId();
        PreferenceCategory keyboardSettingsCategory = new PreferenceCategory(prefContext);
        root.addPreference(keyboardSettingsCategory);
        PackageManager pm = prefContext.getPackageManager();
        CharSequence label = imi.loadLabel(pm);
        keyboardSettingsCategory.setTitle(label);
        keyboardSettingsCategory.setKey(imiId);
        TwoStatePreference autoSelectionPref = new SwitchWithNoTextPreference(prefContext);
        this.mAutoSelectionPrefsMap.put(imiId, autoSelectionPref);
        keyboardSettingsCategory.addPreference(autoSelectionPref);
        autoSelectionPref.setOnPreferenceChangeListener(this);
        PreferenceCategory activeInputMethodsCategory = new PreferenceCategory(prefContext);
        activeInputMethodsCategory.setTitle(R.string.active_input_method_subtypes);
        root.addPreference(activeInputMethodsCategory);
        CharSequence autoSubtypeLabel = null;
        ArrayList<Preference> subtypePreferences = new ArrayList<>();
        for (int index = 0; index < subtypeCount; index++) {
            InputMethodSubtype subtype = imi.getSubtypeAt(index);
            if (subtype.overridesImplicitlyEnabledSubtype()) {
                if (autoSubtypeLabel == null) {
                    autoSubtypeLabel = InputMethodAndSubtypeUtil.getSubtypeLocaleNameAsSentence(subtype, prefContext, imi);
                }
            } else {
                Preference subtypePref = new InputMethodSubtypePreference(prefContext, subtype, imi);
                subtypePreferences.add(subtypePref);
            }
        }
        subtypePreferences.sort(new Comparator() { // from class: com.android.settingslib.inputmethod.-$$Lambda$InputMethodAndSubtypeEnablerManager$PPMWeI2GfPQjVpSU0RU1gADruK4
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return InputMethodAndSubtypeEnablerManager.this.lambda$addInputMethodSubtypePreferences$0$InputMethodAndSubtypeEnablerManager((Preference) obj, (Preference) obj2);
            }
        });
        Iterator<Preference> it = subtypePreferences.iterator();
        while (it.hasNext()) {
            Preference pref = it.next();
            activeInputMethodsCategory.addPreference(pref);
            pref.setOnPreferenceChangeListener(this);
            InputMethodAndSubtypeUtil.removeUnnecessaryNonPersistentPreference(pref);
        }
        this.mInputMethodAndSubtypePrefsMap.put(imiId, subtypePreferences);
        if (TextUtils.isEmpty(autoSubtypeLabel)) {
            autoSelectionPref.setTitle(R.string.use_system_language_to_select_input_method_subtypes);
        } else {
            autoSelectionPref.setTitle(autoSubtypeLabel);
        }
    }

    public /* synthetic */ int lambda$addInputMethodSubtypePreferences$0$InputMethodAndSubtypeEnablerManager(Preference lhs, Preference rhs) {
        if (lhs instanceof InputMethodSubtypePreference) {
            return ((InputMethodSubtypePreference) lhs).compareTo(rhs, this.mCollator);
        }
        return lhs.compareTo(rhs);
    }

    private boolean isNoSubtypesExplicitlySelected(String imiId) {
        List<Preference> subtypePrefs = this.mInputMethodAndSubtypePrefsMap.get(imiId);
        for (Preference pref : subtypePrefs) {
            if ((pref instanceof TwoStatePreference) && ((TwoStatePreference) pref).isChecked()) {
                return false;
            }
        }
        return true;
    }

    private void setAutoSelectionSubtypesEnabled(String imiId, boolean autoSelectionEnabled) {
        TwoStatePreference autoSelectionPref = this.mAutoSelectionPrefsMap.get(imiId);
        if (autoSelectionPref == null) {
            return;
        }
        autoSelectionPref.setChecked(autoSelectionEnabled);
        List<Preference> subtypePrefs = this.mInputMethodAndSubtypePrefsMap.get(imiId);
        for (Preference pref : subtypePrefs) {
            if (pref instanceof TwoStatePreference) {
                pref.setEnabled(!autoSelectionEnabled);
                if (autoSelectionEnabled) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            }
        }
        if (autoSelectionEnabled) {
            PreferenceFragment preferenceFragment = this.mFragment;
            InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(preferenceFragment, preferenceFragment.getContext().getContentResolver(), this.mInputMethodInfoList, this.mHaveHardKeyboard);
            updateImplicitlyEnabledSubtypes(imiId);
        }
    }

    private void updateImplicitlyEnabledSubtypes(String targetImiId) {
        for (InputMethodInfo imi : this.mInputMethodInfoList) {
            String imiId = imi.getId();
            TwoStatePreference autoSelectionPref = this.mAutoSelectionPrefsMap.get(imiId);
            if (autoSelectionPref != null && autoSelectionPref.isChecked() && (imiId.equals(targetImiId) || targetImiId == null)) {
                updateImplicitlyEnabledSubtypesOf(imi);
            }
        }
    }

    private void updateImplicitlyEnabledSubtypesOf(InputMethodInfo imi) {
        String imiId = imi.getId();
        List<Preference> subtypePrefs = this.mInputMethodAndSubtypePrefsMap.get(imiId);
        List<InputMethodSubtype> implicitlyEnabledSubtypes = this.mImm.getEnabledInputMethodSubtypeList(imi, true);
        if (subtypePrefs == null || implicitlyEnabledSubtypes == null) {
            return;
        }
        for (Preference pref : subtypePrefs) {
            if (pref instanceof TwoStatePreference) {
                TwoStatePreference subtypePref = (TwoStatePreference) pref;
                subtypePref.setChecked(false);
                Iterator<InputMethodSubtype> it = implicitlyEnabledSubtypes.iterator();
                while (true) {
                    if (it.hasNext()) {
                        InputMethodSubtype subtype = it.next();
                        String implicitlyEnabledSubtypePrefKey = imiId + subtype.hashCode();
                        if (subtypePref.getKey().equals(implicitlyEnabledSubtypePrefKey)) {
                            subtypePref.setChecked(true);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void updateAutoSelectionPreferences() {
        for (String imiId : this.mInputMethodAndSubtypePrefsMap.keySet()) {
            setAutoSelectionSubtypesEnabled(imiId, isNoSubtypesExplicitlySelected(imiId));
        }
        updateImplicitlyEnabledSubtypes(null);
    }
}
