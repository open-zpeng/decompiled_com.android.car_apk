package com.android.settingslib.license;

import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import androidx.annotation.VisibleForTesting;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
class LicenseHtmlGeneratorFromXml {
    private static final String ATTR_CONTENT_ID = "contentId";
    private static final String HTML_HEAD_STRING = "<html><head>\n<style type=\"text/css\">\nbody { padding: 0; font-family: sans-serif; }\n.same-license { background-color: #eeeeee;\n                border-top: 20px solid white;\n                padding: 10px; }\n.label { font-weight: bold; }\n.file-list { margin-left: 1em; color: blue; }\n</style>\n</head><body topmargin=\"0\" leftmargin=\"0\" rightmargin=\"0\" bottommargin=\"0\">\n<div class=\"toc\">\n<ul>";
    private static final String HTML_MIDDLE_STRING = "</ul>\n</div><!-- table of contents -->\n<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">";
    private static final String HTML_REAR_STRING = "</table></body></html>";
    private static final String TAG = "LicenseGeneratorFromXml";
    private static final String TAG_FILE_CONTENT = "file-content";
    private static final String TAG_FILE_NAME = "file-name";
    private static final String TAG_ROOT = "licenses";
    private final List<File> mXmlFiles;
    private final Map<String, String> mFileNameToContentIdMap = new HashMap();
    private final Map<String, String> mContentIdToFileContentMap = new HashMap();

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static class ContentIdAndFileNames {
        final String mContentId;
        final List<String> mFileNameList = new ArrayList();

        ContentIdAndFileNames(String contentId) {
            this.mContentId = contentId;
        }
    }

    private LicenseHtmlGeneratorFromXml(List<File> xmlFiles) {
        this.mXmlFiles = xmlFiles;
    }

    public static boolean generateHtml(List<File> xmlFiles, File outputFile, String noticeHeader) {
        LicenseHtmlGeneratorFromXml genertor = new LicenseHtmlGeneratorFromXml(xmlFiles);
        return genertor.generateHtml(outputFile, noticeHeader);
    }

    private boolean generateHtml(File outputFile, String noticeHeader) {
        for (File xmlFile : this.mXmlFiles) {
            parse(xmlFile);
        }
        if (this.mFileNameToContentIdMap.isEmpty() || this.mContentIdToFileContentMap.isEmpty()) {
            return false;
        }
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(outputFile);
            generateHtml(this.mFileNameToContentIdMap, this.mContentIdToFileContentMap, writer, noticeHeader);
            writer.flush();
            writer.close();
            return true;
        } catch (FileNotFoundException | SecurityException e) {
            Log.e(TAG, "Failed to generate " + outputFile, e);
            if (writer != null) {
                writer.close();
            }
            return false;
        }
    }

    private void parse(File xmlFile) {
        if (xmlFile == null || !xmlFile.exists() || xmlFile.length() == 0) {
            return;
        }
        InputStreamReader in = null;
        try {
            if (xmlFile.getName().endsWith(".gz")) {
                in = new InputStreamReader(new GZIPInputStream(new FileInputStream(xmlFile)));
            } else {
                in = new FileReader(xmlFile);
            }
            parse(in, this.mFileNameToContentIdMap, this.mContentIdToFileContentMap);
            in.close();
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Failed to parse " + xmlFile, e);
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e2) {
                    Log.w(TAG, "Failed to close " + xmlFile);
                }
            }
        }
    }

    @VisibleForTesting
    static void parse(InputStreamReader in, Map<String, String> outFileNameToContentIdMap, Map<String, String> outContentIdToFileContentMap) throws XmlPullParserException, IOException {
        Map<String, String> fileNameToContentIdMap = new HashMap<>();
        Map<String, String> contentIdToFileContentMap = new HashMap<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in);
        parser.nextTag();
        parser.require(2, "", TAG_ROOT);
        for (int state = parser.getEventType(); state != 1; state = parser.next()) {
            if (state == 2) {
                if (TAG_FILE_NAME.equals(parser.getName())) {
                    String contentId = parser.getAttributeValue("", ATTR_CONTENT_ID);
                    if (!TextUtils.isEmpty(contentId)) {
                        String fileName = readText(parser).trim();
                        if (!TextUtils.isEmpty(fileName)) {
                            fileNameToContentIdMap.put(fileName, contentId);
                        }
                    }
                } else if (TAG_FILE_CONTENT.equals(parser.getName())) {
                    String contentId2 = parser.getAttributeValue("", ATTR_CONTENT_ID);
                    if (!TextUtils.isEmpty(contentId2) && !outContentIdToFileContentMap.containsKey(contentId2) && !contentIdToFileContentMap.containsKey(contentId2)) {
                        String fileContent = readText(parser);
                        if (!TextUtils.isEmpty(fileContent)) {
                            contentIdToFileContentMap.put(contentId2, fileContent);
                        }
                    }
                }
            }
        }
        outFileNameToContentIdMap.putAll(fileNameToContentIdMap);
        outContentIdToFileContentMap.putAll(contentIdToFileContentMap);
    }

    private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        StringBuffer result = new StringBuffer();
        int state = parser.next();
        while (state == 4) {
            result.append(parser.getText());
            state = parser.next();
        }
        return result.toString();
    }

    @VisibleForTesting
    static void generateHtml(Map<String, String> fileNameToContentIdMap, Map<String, String> contentIdToFileContentMap, PrintWriter writer, String noticeHeader) {
        List<String> fileNameList = new ArrayList<>();
        fileNameList.addAll(fileNameToContentIdMap.keySet());
        Collections.sort(fileNameList);
        writer.println(HTML_HEAD_STRING);
        if (!TextUtils.isEmpty(noticeHeader)) {
            writer.println(noticeHeader);
        }
        int count = 0;
        Map<String, Integer> contentIdToOrderMap = new HashMap<>();
        List<ContentIdAndFileNames> contentIdAndFileNamesList = new ArrayList<>();
        for (String fileName : fileNameList) {
            String contentId = fileNameToContentIdMap.get(fileName);
            if (!contentIdToOrderMap.containsKey(contentId)) {
                contentIdToOrderMap.put(contentId, Integer.valueOf(count));
                contentIdAndFileNamesList.add(new ContentIdAndFileNames(contentId));
                count++;
            }
            int id = contentIdToOrderMap.get(contentId).intValue();
            contentIdAndFileNamesList.get(id).mFileNameList.add(fileName);
            writer.format("<li><a href=\"#id%d\">%s</a></li>\n", Integer.valueOf(id), fileName);
        }
        writer.println(HTML_MIDDLE_STRING);
        int count2 = 0;
        for (ContentIdAndFileNames contentIdAndFileNames : contentIdAndFileNamesList) {
            writer.format("<tr id=\"id%d\"><td class=\"same-license\">\n", Integer.valueOf(count2));
            writer.println("<div class=\"label\">Notices for file(s):</div>");
            writer.println("<div class=\"file-list\">");
            Iterator<String> it = contentIdAndFileNames.mFileNameList.iterator();
            while (it.hasNext()) {
                writer.format("%s <br/>\n", it.next());
            }
            writer.println("</div><!-- file-list -->");
            writer.println("<pre class=\"license-text\">");
            writer.println(contentIdToFileContentMap.get(contentIdAndFileNames.mContentId));
            writer.println("</pre><!-- license-text -->");
            writer.println("</td></tr><!-- same-license -->");
            count2++;
        }
        writer.println(HTML_REAR_STRING);
    }
}
