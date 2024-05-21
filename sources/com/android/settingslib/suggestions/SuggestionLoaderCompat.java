package com.android.settingslib.suggestions;

import android.content.Context;
import android.service.settings.suggestions.Suggestion;
import android.util.Log;
import com.android.settingslib.utils.AsyncLoaderCompat;
import java.util.List;
/* loaded from: classes3.dex */
public class SuggestionLoaderCompat extends AsyncLoaderCompat<List<Suggestion>> {
    public static final int LOADER_ID_SUGGESTIONS = 42;
    private static final String TAG = "SuggestionLoader";
    private final SuggestionController mSuggestionController;

    public SuggestionLoaderCompat(Context context, SuggestionController controller) {
        super(context);
        this.mSuggestionController = controller;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.settingslib.utils.AsyncLoaderCompat
    public void onDiscardResult(List<Suggestion> result) {
    }

    @Override // androidx.loader.content.AsyncTaskLoader
    public List<Suggestion> loadInBackground() {
        List<Suggestion> data = this.mSuggestionController.getSuggestions();
        if (data == null) {
            Log.d(TAG, "data is null");
        } else {
            Log.d(TAG, "data size " + data.size());
        }
        return data;
    }
}
