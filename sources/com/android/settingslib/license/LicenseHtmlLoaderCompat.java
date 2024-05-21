package com.android.settingslib.license;

import android.content.Context;
import android.util.Log;
import com.android.settingslib.R;
import com.android.settingslib.utils.AsyncLoaderCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class LicenseHtmlLoaderCompat extends AsyncLoaderCompat<File> {
    static final String[] DEFAULT_LICENSE_XML_PATHS = {"/system/etc/NOTICE.xml.gz", "/vendor/etc/NOTICE.xml.gz", "/odm/etc/NOTICE.xml.gz", "/oem/etc/NOTICE.xml.gz", "/product/etc/NOTICE.xml.gz", "/product_services/etc/NOTICE.xml.gz"};
    static final String NOTICE_HTML_FILE_NAME = "NOTICE.html";
    private static final String TAG = "LicenseHtmlLoaderCompat";
    private final Context mContext;

    public LicenseHtmlLoaderCompat(Context context) {
        super(context);
        this.mContext = context;
    }

    @Override // androidx.loader.content.AsyncTaskLoader
    public File loadInBackground() {
        return generateHtmlFromDefaultXmlFiles();
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.settingslib.utils.AsyncLoaderCompat
    public void onDiscardResult(File f) {
    }

    private File generateHtmlFromDefaultXmlFiles() {
        List<File> xmlFiles = getVaildXmlFiles();
        if (xmlFiles.isEmpty()) {
            Log.e(TAG, "No notice file exists.");
            return null;
        }
        File cachedHtmlFile = getCachedHtmlFile(this.mContext);
        if (!isCachedHtmlFileOutdated(xmlFiles, cachedHtmlFile) || generateHtmlFile(this.mContext, xmlFiles, cachedHtmlFile)) {
            return cachedHtmlFile;
        }
        return null;
    }

    private List<File> getVaildXmlFiles() {
        String[] strArr;
        List<File> xmlFiles = new ArrayList<>();
        for (String xmlPath : DEFAULT_LICENSE_XML_PATHS) {
            File file = new File(xmlPath);
            if (file.exists() && file.length() != 0) {
                xmlFiles.add(file);
            }
        }
        return xmlFiles;
    }

    private File getCachedHtmlFile(Context context) {
        return new File(context.getCacheDir(), NOTICE_HTML_FILE_NAME);
    }

    private boolean isCachedHtmlFileOutdated(List<File> xmlFiles, File cachedHtmlFile) {
        if (!cachedHtmlFile.exists() || cachedHtmlFile.length() == 0) {
            return true;
        }
        for (File file : xmlFiles) {
            if (cachedHtmlFile.lastModified() < file.lastModified()) {
                return true;
            }
        }
        return false;
    }

    private boolean generateHtmlFile(Context context, List<File> xmlFiles, File htmlFile) {
        return LicenseHtmlGeneratorFromXml.generateHtml(xmlFiles, htmlFile, context.getString(R.string.notice_header));
    }
}
