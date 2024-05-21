package com.android.settingslib.core.lifecycle;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
/* loaded from: classes3.dex */
public class ObservableDialogFragment extends DialogFragment implements LifecycleOwner {
    protected final Lifecycle mLifecycle = new Lifecycle(this);

    public Lifecycle getSettingsLifecycle() {
        return this.mLifecycle;
    }

    @Override // androidx.fragment.app.DialogFragment, androidx.fragment.app.Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
        this.mLifecycle.onAttach(context);
    }

    @Override // androidx.fragment.app.DialogFragment, androidx.fragment.app.Fragment
    public void onCreate(Bundle savedInstanceState) {
        this.mLifecycle.onCreate(savedInstanceState);
        this.mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        super.onCreate(savedInstanceState);
    }

    @Override // androidx.fragment.app.DialogFragment, androidx.fragment.app.Fragment
    public void onStart() {
        this.mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
        super.onStart();
    }

    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        this.mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        super.onResume();
    }

    @Override // androidx.fragment.app.Fragment
    public void onPause() {
        this.mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        super.onPause();
    }

    @Override // androidx.fragment.app.DialogFragment, androidx.fragment.app.Fragment
    public void onStop() {
        this.mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override // androidx.fragment.app.Fragment
    public void onDestroy() {
        this.mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.mLifecycle.onCreateOptionsMenu(menu, inflater);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override // androidx.fragment.app.Fragment
    public void onPrepareOptionsMenu(Menu menu) {
        this.mLifecycle.onPrepareOptionsMenu(menu);
        super.onPrepareOptionsMenu(menu);
    }

    @Override // androidx.fragment.app.Fragment
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        boolean lifecycleHandled = this.mLifecycle.onOptionsItemSelected(menuItem);
        if (!lifecycleHandled) {
            return super.onOptionsItemSelected(menuItem);
        }
        return lifecycleHandled;
    }
}
