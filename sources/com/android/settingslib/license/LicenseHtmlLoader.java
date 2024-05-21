package com.android.settingslib.license;

import android.content.Context;
import com.android.settingslib.utils.AsyncLoader;
import java.io.File;
/* loaded from: classes3.dex */
public class LicenseHtmlLoader extends AsyncLoader<File> {
    private static final String TAG = "LicenseHtmlLoader";
    private Context mContext;

    public LicenseHtmlLoader(Context context) {
        super(context);
        this.mContext = context;
    }

    @Override // android.content.AsyncTaskLoader
    public File loadInBackground() {
        return new LicenseHtmlLoaderCompat(this.mContext).loadInBackground();
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.settingslib.utils.AsyncLoader
    public void onDiscardResult(File f) {
    }
}
