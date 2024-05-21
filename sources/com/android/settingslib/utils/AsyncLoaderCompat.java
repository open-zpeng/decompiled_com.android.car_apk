package com.android.settingslib.utils;

import android.content.Context;
import androidx.loader.content.AsyncTaskLoader;
/* loaded from: classes3.dex */
public abstract class AsyncLoaderCompat<T> extends AsyncTaskLoader<T> {
    private T mResult;

    protected abstract void onDiscardResult(T t);

    public AsyncLoaderCompat(Context context) {
        super(context);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onStartLoading() {
        T t = this.mResult;
        if (t != null) {
            deliverResult(t);
        }
        if (takeContentChanged() || this.mResult == null) {
            forceLoad();
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onStopLoading() {
        cancelLoad();
    }

    @Override // androidx.loader.content.Loader
    public void deliverResult(T data) {
        if (isReset()) {
            if (data != null) {
                onDiscardResult(data);
                return;
            }
            return;
        }
        T oldResult = this.mResult;
        this.mResult = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
        if (oldResult != null && oldResult != this.mResult) {
            onDiscardResult(oldResult);
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onReset() {
        super.onReset();
        onStopLoading();
        T t = this.mResult;
        if (t != null) {
            onDiscardResult(t);
        }
        this.mResult = null;
    }

    @Override // androidx.loader.content.AsyncTaskLoader
    public void onCanceled(T data) {
        super.onCanceled(data);
        if (data != null) {
            onDiscardResult(data);
        }
    }
}
