package com.android.settingslib.inputmethod;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes3.dex */
public class InputMethodSettingValuesWrapper {
    private static final String TAG = InputMethodSettingValuesWrapper.class.getSimpleName();
    private static volatile InputMethodSettingValuesWrapper sInstance;
    private final ContentResolver mContentResolver;
    private final InputMethodManager mImm;
    private final ArrayList<InputMethodInfo> mMethodList = new ArrayList<>();

    public static InputMethodSettingValuesWrapper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (TAG) {
                if (sInstance == null) {
                    sInstance = new InputMethodSettingValuesWrapper(context);
                }
            }
        }
        return sInstance;
    }

    private InputMethodSettingValuesWrapper(Context context) {
        this.mContentResolver = context.getContentResolver();
        this.mImm = (InputMethodManager) context.getSystemService(InputMethodManager.class);
        refreshAllInputMethodAndSubtypes();
    }

    public void refreshAllInputMethodAndSubtypes() {
        this.mMethodList.clear();
        this.mMethodList.addAll(this.mImm.getInputMethodList());
    }

    public List<InputMethodInfo> getInputMethodList() {
        return new ArrayList(this.mMethodList);
    }

    public boolean isAlwaysCheckedIme(InputMethodInfo imi) {
        boolean isEnabled = isEnabledImi(imi);
        if (getEnabledInputMethodList().size() > 1 || !isEnabled) {
            int enabledValidNonAuxAsciiCapableImeCount = getEnabledValidNonAuxAsciiCapableImeCount();
            return enabledValidNonAuxAsciiCapableImeCount <= 1 && (enabledValidNonAuxAsciiCapableImeCount != 1 || isEnabled) && imi.isSystem() && InputMethodAndSubtypeUtil.isValidNonAuxAsciiCapableIme(imi);
        }
        return true;
    }

    private int getEnabledValidNonAuxAsciiCapableImeCount() {
        int count = 0;
        List<InputMethodInfo> enabledImis = getEnabledInputMethodList();
        for (InputMethodInfo imi : enabledImis) {
            if (InputMethodAndSubtypeUtil.isValidNonAuxAsciiCapableIme(imi)) {
                count++;
            }
        }
        if (count == 0) {
            Log.w(TAG, "No \"enabledValidNonAuxAsciiCapableIme\"s found.");
        }
        return count;
    }

    public boolean isEnabledImi(InputMethodInfo imi) {
        List<InputMethodInfo> enabledImis = getEnabledInputMethodList();
        for (InputMethodInfo tempImi : enabledImis) {
            if (tempImi.getId().equals(imi.getId())) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<InputMethodInfo> getEnabledInputMethodList() {
        HashMap<String, HashSet<String>> enabledInputMethodsAndSubtypes = InputMethodAndSubtypeUtil.getEnabledInputMethodsAndSubtypeList(this.mContentResolver);
        ArrayList<InputMethodInfo> result = new ArrayList<>();
        Iterator<InputMethodInfo> it = this.mMethodList.iterator();
        while (it.hasNext()) {
            InputMethodInfo imi = it.next();
            if (enabledInputMethodsAndSubtypes.keySet().contains(imi.getId())) {
                result.add(imi);
            }
        }
        return result;
    }
}
