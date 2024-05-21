package com.android.settingslib.core.lifecycle;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.core.lifecycle.events.OnAttach;
import com.android.settingslib.core.lifecycle.events.OnCreate;
import com.android.settingslib.core.lifecycle.events.OnCreateOptionsMenu;
import com.android.settingslib.core.lifecycle.events.OnDestroy;
import com.android.settingslib.core.lifecycle.events.OnOptionsItemSelected;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnPrepareOptionsMenu;
import com.android.settingslib.core.lifecycle.events.OnResume;
import com.android.settingslib.core.lifecycle.events.OnSaveInstanceState;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.core.lifecycle.events.SetPreferenceScreen;
import com.android.settingslib.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class Lifecycle extends LifecycleRegistry {
    private static final String TAG = "LifecycleObserver";
    private final List<LifecycleObserver> mObservers;
    private final LifecycleProxy mProxy;

    public Lifecycle(@NonNull LifecycleOwner provider) {
        super(provider);
        this.mObservers = new ArrayList();
        this.mProxy = new LifecycleProxy();
        addObserver(this.mProxy);
    }

    @Override // androidx.lifecycle.LifecycleRegistry, androidx.lifecycle.Lifecycle
    public void addObserver(androidx.lifecycle.LifecycleObserver observer) {
        ThreadUtils.ensureMainThread();
        super.addObserver(observer);
        if (observer instanceof LifecycleObserver) {
            this.mObservers.add((LifecycleObserver) observer);
        }
    }

    @Override // androidx.lifecycle.LifecycleRegistry, androidx.lifecycle.Lifecycle
    public void removeObserver(androidx.lifecycle.LifecycleObserver observer) {
        ThreadUtils.ensureMainThread();
        super.removeObserver(observer);
        if (observer instanceof LifecycleObserver) {
            this.mObservers.remove(observer);
        }
    }

    public void onAttach(Context context) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnAttach) {
                ((OnAttach) observer).onAttach(context);
            }
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnCreate) {
                ((OnCreate) observer).onCreate(savedInstanceState);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onStart() {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnStart) {
                ((OnStart) observer).onStart();
            }
        }
    }

    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof SetPreferenceScreen) {
                ((SetPreferenceScreen) observer).setPreferenceScreen(preferenceScreen);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onResume() {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnResume) {
                ((OnResume) observer).onResume();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onPause() {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnPause) {
                ((OnPause) observer).onPause();
            }
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnSaveInstanceState) {
                ((OnSaveInstanceState) observer).onSaveInstanceState(outState);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onStop() {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnStop) {
                ((OnStop) observer).onStop();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDestroy() {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnDestroy) {
                ((OnDestroy) observer).onDestroy();
            }
        }
    }

    public void onCreateOptionsMenu(Menu menu, @Nullable MenuInflater inflater) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnCreateOptionsMenu) {
                ((OnCreateOptionsMenu) observer).onCreateOptionsMenu(menu, inflater);
            }
        }
    }

    public void onPrepareOptionsMenu(Menu menu) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if (observer instanceof OnPrepareOptionsMenu) {
                ((OnPrepareOptionsMenu) observer).onPrepareOptionsMenu(menu);
            }
        }
    }

    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int size = this.mObservers.size();
        for (int i = 0; i < size; i++) {
            LifecycleObserver observer = this.mObservers.get(i);
            if ((observer instanceof OnOptionsItemSelected) && ((OnOptionsItemSelected) observer).onOptionsItemSelected(menuItem)) {
                return true;
            }
        }
        return false;
    }

    /* loaded from: classes3.dex */
    private class LifecycleProxy implements androidx.lifecycle.LifecycleObserver {
        private LifecycleProxy() {
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
        public void onLifecycleEvent(LifecycleOwner owner, Lifecycle.Event event) {
            switch (event) {
                case ON_CREATE:
                default:
                    return;
                case ON_START:
                    Lifecycle.this.onStart();
                    return;
                case ON_RESUME:
                    Lifecycle.this.onResume();
                    return;
                case ON_PAUSE:
                    Lifecycle.this.onPause();
                    return;
                case ON_STOP:
                    Lifecycle.this.onStop();
                    return;
                case ON_DESTROY:
                    Lifecycle.this.onDestroy();
                    return;
                case ON_ANY:
                    Log.wtf(Lifecycle.TAG, "Should not receive an 'ANY' event!");
                    return;
            }
        }
    }
}
