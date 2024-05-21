package com.android.car.permission;

import android.annotation.TargetApi;
import android.car.XpDebugLog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Slog;
import android.util.Xml;
import com.android.car.CarPropertyService;
import com.android.car.CarServiceBase;
import com.android.car.ProcessUtils;
import com.android.car.permission.CarSignature;
import com.android.car.utils.EncryptUtils;
import com.android.car.utils.FileIOUtils;
import com.android.car.utils.FileUtils;
import com.android.internal.util.XmlUtils;
import com.android.settingslib.datetime.ZoneGetter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
/* loaded from: classes3.dex */
public class CarPermissionManagerService implements CarServiceBase {
    private static final String FILE_NAME_EXTENSION_JSON = "json";
    private static final String FILE_NAME_EXTENSION_XML = "xml";
    private static final String FILE_PERMISSION_APP = "/system/etc/car/app/";
    private static final String FILE_PERMISSION_SYS = "/system/etc/car/system/";
    private static final String FILE_PERMISSION_UID = "/system/etc/car/uid/";
    private static final String FILE_WHITE_BLACK_LIST = "/system/etc/car/list/white_black_list.json";
    private static final String FILE_WHITE_BLACK_XML_FILE = "/system/etc/car/list/white_black_list.xml";
    public static final int PACKAGE_ROLE_ALLOWED = 1;
    public static final int PACKAGE_ROLE_FORBIDDEN = 1;
    public static final int PACKAGE_ROLE_NONE = 0;
    public static final int PERMISSION_DENIED_APPID_INVALID = 3;
    public static final int PERMISSION_DENIED_APPID_NULL = 2;
    public static final int PERMISSION_DENIED_APPID_UNSUPPORTED = 4;
    public static final int PERMISSION_DENIED_CONFIG_NULL = 5;
    public static final int PERMISSION_DENIED_READ_OR_WRITE = 8;
    public static final int PERMISSION_DENIED_UNKNOWN = 1;
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_REQ_READ = 1;
    public static final int PERMISSION_REQ_READ_WRITE = 3;
    public static final int PERMISSION_REQ_WRITE = 2;
    private static final String PROP_CAR_PERMISSION_CHECK_DEBUG_APP_FLAG = "persist.car.permission.check.debug.app.flag";
    private static final String PROP_CAR_PERMISSION_DEBUG_COST_NAME = "persist.car.permission.debug.cost.name";
    private static final String PROP_CAR_PERMISSION_DEBUG_COST_TIME = "persist.car.permission.debug.cost.time";
    private static final long PROP_CAR_PERMISSION_DEBUG_COST_TIME_DEFAULT = 10;
    private static final String PROP_CAR_PERMISSION_DEBUG_LOG_PKG = "persist.car.permission.debug.log.pkg";
    private static final String PROP_CAR_PERMISSION_ENABLE = "persist.car.permission.enable";
    private static final String PROP_CAR_PERMISSION_SECURITY_EXCEPTION = "persist.car.permission.security.exception";
    public static final String SALT = "xiapeng_carservice";
    private static final String TAG = "CarPermissionManager";
    private static final int WHITE_BLACK_LIST_BLACK = 2;
    private static final int WHITE_BLACK_LIST_NONE = 0;
    private static final int WHITE_BLACK_LIST_WHITE = 1;
    private final CarPropertyService mCarPropertyService;
    private int mCheckAppFlag;
    private Context mContext;
    private String mDebugLogPkgName;
    private boolean mIsCarPermissionEnable;
    private String mIsDebugCostPropName;
    private long mIsDebugCostTime;
    private final boolean mIsUser;
    PackageManager mPackageManager;
    private final Handler mWorkHandler;
    private static final Object sDeniedLock = new Object();
    private static final Object sDataLock = new Object();
    private static final Object sCostLock = new Object();
    private final Map<String, CarSignature> mCarSignatureMap = new ConcurrentHashMap();
    private final Map<String, Integer> mWhiteBlackMap = new ConcurrentHashMap();
    private final Map<String, String> mCacheSignatureMap = new ConcurrentHashMap();
    private final Map<String, Map<String, PropertyPermissionInfo>> mDeniedPackageMaps = new ConcurrentHashMap();
    private final Map<String, Map<String, PropertyPermissionInfo>> mCostPackageMaps = new ConcurrentHashMap();
    private final Map<String, BindAppInfo> mCacheBindAppInfoMap = new ConcurrentHashMap();
    private final Map<Integer, String> mCacheBindPackages = new ConcurrentHashMap();

    public CarPermissionManagerService(Context context, CarPropertyService propertyService) {
        Slog.i("CarPermissionManager", "CarPermissionManagerService: onCreate()");
        this.mIsUser = Build.IS_USER;
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
        this.mCarPropertyService = propertyService;
        HandlerThread workThread = new HandlerThread("car-permission-thread");
        workThread.start();
        this.mWorkHandler = new Handler(workThread.getLooper());
        initCarPermissionData();
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
    }

    public Map<String, CarSignature> getCarSignatureMap() {
        return this.mCarSignatureMap;
    }

    public Map<String, String> getCacheSignatureMap() {
        return this.mCacheSignatureMap;
    }

    public void initCarPermissionData() {
        this.mDebugLogPkgName = SystemProperties.get(PROP_CAR_PERMISSION_DEBUG_LOG_PKG, "");
        this.mIsDebugCostPropName = SystemProperties.get(PROP_CAR_PERMISSION_DEBUG_COST_NAME, "");
        this.mIsDebugCostTime = SystemProperties.getLong(PROP_CAR_PERMISSION_DEBUG_COST_TIME, (long) PROP_CAR_PERMISSION_DEBUG_COST_TIME_DEFAULT);
        this.mIsCarPermissionEnable = SystemProperties.getBoolean(PROP_CAR_PERMISSION_ENABLE, false);
        this.mCheckAppFlag = SystemProperties.getInt(PROP_CAR_PERMISSION_CHECK_DEBUG_APP_FLAG, 0);
        Slog.i("CarPermissionManager", "initCarPermissionData: mIsCarPermissionEnable=" + this.mIsCarPermissionEnable);
        Slog.i("CarPermissionManager", "initCarPermissionData: mCheckAppFlag=" + this.mCheckAppFlag);
        Slog.i("CarPermissionManager", "initCarPermissionData: mIsDebugCostTime=" + this.mIsDebugCostTime);
        Slog.i("CarPermissionManager", "initCarPermissionData: mDebugLogPkgName=" + this.mDebugLogPkgName);
        if (this.mIsCarPermissionEnable) {
            this.mWorkHandler.post(new Runnable() { // from class: com.android.car.permission.-$$Lambda$CarPermissionManagerService$-3g3YECwVnfJD8SXSUhISA2q5Vw
                @Override // java.lang.Runnable
                public final void run() {
                    CarPermissionManagerService.this.lambda$initCarPermissionData$0$CarPermissionManagerService();
                }
            });
        }
    }

    public /* synthetic */ void lambda$initCarPermissionData$0$CarPermissionManagerService() {
        Slog.i("CarPermissionManager", "parsePermissionFiles: parse begin.");
        synchronized (sDataLock) {
            this.mCarSignatureMap.clear();
            parsePermissionXmlFiles(FILE_PERMISSION_APP);
            parsePermissionXmlFiles(FILE_PERMISSION_SYS);
            parsePermissionXmlFiles(FILE_PERMISSION_UID);
            this.mWhiteBlackMap.clear();
            parseWhiteBlackListXmlFile(FILE_WHITE_BLACK_XML_FILE);
        }
        if (!this.mIsUser) {
            Slog.i("CarPermissionManager", "parsePermissionFiles: size=" + this.mCarSignatureMap.size());
            Slog.i("CarPermissionManager", "parseWhiteBlackListFiles: size=" + this.mWhiteBlackMap.size());
        }
        Slog.i("CarPermissionManager", "parsePermissionFiles: parse done.");
    }

    private void parsePermissionFiles(String filePath) {
        if (!FileUtils.isDir(filePath)) {
            Slog.e("CarPermissionManager", "parsePermissionFiles: " + filePath + " is not found!");
            return;
        }
        List<File> files = FileUtils.listFilesInDir(filePath);
        for (File file : files) {
            try {
                String fileString = FileIOUtils.readFile2String(file, "UTF-8");
                String filePkgName = FileUtils.getFileNameNoExtension(file);
                String fileExtension = FileUtils.getFileExtension(file);
                if (FILE_NAME_EXTENSION_JSON.equals(fileExtension)) {
                    if (!this.mIsUser) {
                        Slog.i("CarPermissionManager", "parsePermissionFiles: filePkgName=" + filePkgName + ", fileExtension=" + fileExtension);
                    }
                    JSONObject permissionJson = new JSONObject(fileString);
                    String packageName = permissionJson.optString("packageName", "");
                    if (!TextUtils.isEmpty(packageName) && filePkgName.equals(packageName)) {
                        if (this.mCarSignatureMap.get(packageName) != null) {
                            Slog.e("CarPermissionManager", "parsePermissionFiles: Duplicate definition. files=" + files + "; packageName=" + packageName);
                        } else {
                            this.mCarSignatureMap.put(packageName, parseSignatureObject(packageName, permissionJson));
                        }
                    }
                    Slog.w("CarPermissionManager", "parsePermissionFiles: packageName is invalid. fileName=" + filePkgName + "; packageName=" + packageName);
                } else {
                    Slog.i("CarPermissionManager", "parsePermissionFiles: file:" + file.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void parsePermissionXmlFiles(String filePath) {
        if (!FileUtils.isDir(filePath)) {
            Slog.e("CarPermissionManager", "parsePermissionXmlFiles: " + filePath + " is not found!");
            return;
        }
        List<File> files = FileUtils.listFilesInDir(filePath);
        for (File file : files) {
            try {
                String filePkgName = FileUtils.getFileNameNoExtension(file);
                String fileExtension = FileUtils.getFileExtension(file);
                if (FILE_NAME_EXTENSION_XML.equals(fileExtension)) {
                    if (!this.mIsUser) {
                        Slog.i("CarPermissionManager", "parsePermissionXmlFiles: filePkgName=" + filePkgName + ", fileExtension=" + fileExtension);
                    }
                    JSONObject permissionJson = parsePermissionXmlFile(file);
                    String packageName = permissionJson.optString("packageName", "");
                    if (!TextUtils.isEmpty(packageName) && filePkgName.equals(packageName)) {
                        logDebug(packageName, "parsePermissionXmlFiles: permissionJson:" + permissionJson);
                        if (this.mCarSignatureMap.get(packageName) != null) {
                            Slog.e("CarPermissionManager", "parsePermissionXmlFiles: Duplicate definition. files=" + files + "; packageName=" + packageName);
                        } else {
                            this.mCarSignatureMap.put(packageName, parseSignatureObject(packageName, permissionJson));
                        }
                    }
                    Slog.w("CarPermissionManager", "parsePermissionXmlFiles: packageName is invalid. fileName=" + filePkgName + "; packageName=" + packageName);
                } else {
                    Slog.i("CarPermissionManager", "parsePermissionXmlFiles: file:" + file.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public JSONObject parsePermissionXmlFile(File file) {
        String access = "des";
        JSONObject pkgObject = new JSONObject();
        try {
            try {
                InputStreamReader confReader = new InputStreamReader(new FileInputStream(file));
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(confReader);
                XmlUtils.beginDocument(parser, "permissions");
                String des = null;
                String appId = parser.getAttributeValue(null, "appId");
                String appId2 = parser.getAttributeValue(null, "appId2");
                String packageName = parser.getAttributeValue(null, "packageName");
                String version = parser.getAttributeValue(null, "version");
                pkgObject.putOpt("appId", appId);
                pkgObject.putOpt("appId2", appId2);
                pkgObject.putOpt("packageName", packageName);
                pkgObject.putOpt("version", version);
                JSONArray propArray = new JSONArray();
                while (true) {
                    XmlUtils.nextElement(parser);
                    String entryName = parser.getName();
                    if (!"prop".equals(entryName)) {
                        break;
                    }
                    JSONObject propObject = new JSONObject();
                    String name = parser.getAttributeValue(des, ZoneGetter.KEY_DISPLAYNAME);
                    String des2 = parser.getAttributeValue(des, access);
                    String access2 = parser.getAttributeValue(des, "access");
                    propObject.putOpt(ZoneGetter.KEY_DISPLAYNAME, name);
                    propObject.putOpt(access, des2);
                    String str = access;
                    propObject.putOpt("access", access2);
                    propArray.put(propObject);
                    access = str;
                    des = null;
                }
                pkgObject.putOpt("property", propArray);
            } catch (Exception e) {
                e = e;
                e.printStackTrace();
                return pkgObject;
            }
        } catch (Exception e2) {
            e = e2;
        }
        return pkgObject;
    }

    public CarSignature parseSignatureObject(String packageName, JSONObject permissionJson) {
        CarSignature signature = new CarSignature();
        signature.mPackageName = packageName;
        signature.mName = permissionJson.optString(ZoneGetter.KEY_DISPLAYNAME);
        signature.mVersion = permissionJson.optInt("version", 0);
        signature.mAppId = permissionJson.optString("appId");
        signature.mAppId2 = permissionJson.optString("appId2");
        if (!this.mIsUser) {
            signature.mSha1 = permissionJson.optString("sha1");
            signature.mSha12 = permissionJson.optString("sha12");
            signature.mEncode = permissionJson.optString("encode");
            signature.mDebug = permissionJson.optInt("debug", 0);
        }
        try {
            JSONArray propAccessArray = permissionJson.getJSONArray("property");
            for (int j = 0; j < propAccessArray.length(); j++) {
                JSONObject propAccessObject = propAccessArray.getJSONObject(j);
                String propName = propAccessObject.optString(ZoneGetter.KEY_DISPLAYNAME);
                if (TextUtils.isEmpty(propName)) {
                    Slog.e("CarPermissionManager", "parseSignatureObject: parse empty propName. files=x; packageName=" + packageName);
                } else {
                    CarSignature.PropAccess propAccess = new CarSignature.PropAccess();
                    propAccess.mName = propName;
                    String access = propAccessObject.optString("access", "r");
                    propAccess.mAccess = CarSignature.getAccessType(access);
                    propAccess.mGranted = propAccessObject.optInt("granted", 1);
                    signature.mPropAccessMap.put(propName, propAccess);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return signature;
    }

    private void parseWhiteBlackListJsonFile(String fileName) {
        if (!FileIOUtils.isFileExists(fileName)) {
            Slog.e("CarPermissionManager", "parseWhiteBlackListFiles: " + fileName + " is not found!");
            return;
        }
        String fileExtension = FileUtils.getFileExtension(fileName);
        Slog.i("CarPermissionManager", "parsePermissionFiles: file:" + fileName);
        if (!FILE_NAME_EXTENSION_XML.equals(fileExtension)) {
            return;
        }
        String fileString = FileIOUtils.readFile2String(fileName, "UTF-8");
        if (TextUtils.isEmpty(fileString)) {
            Slog.e("CarPermissionManager", "parseWhiteBlackListFiles: fileString is null.");
            return;
        }
        if (!this.mIsUser) {
            Slog.i("CarPermissionManager", "parseWhiteBlackListFiles: fileString=" + fileString);
        }
        try {
            JSONArray jsonArray = new JSONArray(fileString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject wbListJson = jsonArray.getJSONObject(i);
                String packageName = wbListJson.optString("packageName");
                int w_or_b = wbListJson.optInt("w_or_b", 0);
                if (!TextUtils.isEmpty(packageName)) {
                    this.mWhiteBlackMap.put(packageName, Integer.valueOf(w_or_b));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void parseWhiteBlackListXmlFile(String fileName) {
        JSONObject pkgObject = new JSONObject();
        File file = FileUtils.getFileByPath(fileName);
        if (!FileIOUtils.isFileExists(file)) {
            Slog.e("CarPermissionManager", "parseWhiteBlackListFiles: " + fileName + " is not found!");
            return;
        }
        try {
            InputStreamReader confReader = new InputStreamReader(new FileInputStream(file));
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(confReader);
            XmlUtils.beginDocument(parser, "permissions");
            String str = null;
            String version = parser.getAttributeValue(null, "version");
            pkgObject.putOpt("version", version);
            JSONArray appArray = new JSONArray();
            while (true) {
                XmlUtils.nextElement(parser);
                String entryName = parser.getName();
                if (!"app".equals(entryName)) {
                    break;
                }
                String packageName = parser.getAttributeValue(str, "packageName");
                int w_or_b = XmlUtils.readIntAttribute(parser, "w_or_b");
                try {
                    this.mWhiteBlackMap.put(packageName, Integer.valueOf(w_or_b));
                    JSONObject propObject = new JSONObject();
                    propObject.putOpt("w_or_b", Integer.valueOf(w_or_b));
                    propObject.putOpt("packageName", packageName);
                    appArray.put(propObject);
                    str = null;
                } catch (Exception e) {
                    e = e;
                    e.printStackTrace();
                    Slog.i("CarPermissionManager", "parseWhiteBlackListXmlFile: pkgObject=" + pkgObject);
                }
            }
            pkgObject.putOpt("apps", appArray);
        } catch (Exception e2) {
            e = e2;
        }
        Slog.i("CarPermissionManager", "parseWhiteBlackListXmlFile: pkgObject=" + pkgObject);
    }

    public static int getRoleType(String value) {
        return 0;
    }

    private boolean checkValidString(String instanceName) {
        if (instanceName != null) {
            for (int i = 0; i < instanceName.length(); i++) {
                char c = instanceName.charAt(i);
                if ((c < 'a' || c > 'z') && ((c < 'A' || c > 'Z') && ((c < '0' || c > '9') && c != '_' && c != '.'))) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    private int getWhiteBlackType(int uid, int pid, String packageName) {
        Integer w_or_b = this.mWhiteBlackMap.get(packageName);
        if (w_or_b == null) {
            w_or_b = this.mWhiteBlackMap.get(String.valueOf(uid));
        }
        if (w_or_b == null) {
            return 0;
        }
        return w_or_b.intValue();
    }

    private String calcPackageNameAppId(String sha1, String packageName) {
        return EncryptUtils.encryptMD5ToString(sha1 + ":" + packageName + ":", SALT);
    }

    private String getManifestAppId(Context context, String packageName) {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(packageName, 128);
            if (appInfo == null || appInfo.metaData == null) {
                return null;
            }
            String appId = appInfo.metaData.getString("com.android.car.v1.apikey");
            return appId;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String dumpDeniedPropInfo() {
        return this.mDeniedPackageMaps.toString();
    }

    public String dumpCostPropInfo() {
        return this.mCostPackageMaps.toString();
    }

    public boolean checkCarPermission(Context context, int propId, int access) {
        String msg;
        if (this.mIsCarPermissionEnable) {
            long costBegin = System.currentTimeMillis();
            String propName = XpDebugLog.getPropertyName(propId);
            int uid = Binder.getCallingUid();
            int pid = Binder.getCallingPid();
            String packageName = ProcessUtils.getProcessName(context, pid, uid);
            int ret = getCarPermission(context, uid, pid, packageName, propId, propName, access);
            saveCostPropInfo(costBegin, packageName, propId, propName, access);
            if (ret != 0) {
                saveDeniedPropInfo(costBegin, packageName, propId, propName, access);
                if (ret == 2) {
                    msg = "PERMISSION_DENIED_APPID_NULL";
                } else if (ret == 3) {
                    msg = "PERMISSION_DENIED_APPID_INVALID";
                } else if (ret == 4) {
                    msg = "PERMISSION_DENIED_APPID_UNSUPPORTED";
                } else if (ret == 5) {
                    msg = "PERMISSION_DENIED_CONFIG_NULL";
                } else if (ret == 8) {
                    msg = "PERMISSION_DENIED_READ_OR_WRITE";
                } else {
                    msg = "PERMISSION_DENIED_UNKNOWN";
                }
                String errMsg = "ret:" + ret + " CarPermissionManager " + XpDebugLog.getPropertyName(propId) + " hasn't grated by " + msg;
                if (SystemProperties.getBoolean(PROP_CAR_PERMISSION_SECURITY_EXCEPTION, false)) {
                    throw new SecurityException("CarPermissionManager has not grated! " + errMsg);
                }
                Slog.e("CarPermissionManager", "CarPermissionManager has not grated! ", new RuntimeException(errMsg));
                return false;
            }
            return true;
        }
        return true;
    }

    private void saveDeniedPropInfo(long costBegin, String packageName, int propId, String propName, int access) {
        if (!this.mIsUser) {
            synchronized (sDeniedLock) {
                Map<String, PropertyPermissionInfo> propertyPermissions = this.mDeniedPackageMaps.get(packageName);
                if (propertyPermissions == null) {
                    propertyPermissions = new ConcurrentHashMap();
                    this.mDeniedPackageMaps.put(packageName, propertyPermissions);
                }
                PropertyPermissionInfo info = propertyPermissions.get(propName);
                if (info == null) {
                    info = new PropertyPermissionInfo();
                    info.mPackageName = packageName;
                    info.mAccess = access;
                    info.mPropId = propId;
                    info.mPropName = propName;
                    info.mCount = 0L;
                    propertyPermissions.put(propName, info);
                } else {
                    info.mCount++;
                    info.mAccess |= access;
                }
                if (this.mIsDebugCostPropName.equals(propName)) {
                    Slog.i("CarPermissionManager", "saveDeniedPropInfo: " + propName + "  info=" + info);
                }
            }
        }
    }

    private void saveCostPropInfo(long costBegin, String packageName, int propId, String propName, int access) {
        if (!this.mIsUser) {
            long costEnd = System.currentTimeMillis();
            long cost = costEnd - costBegin;
            synchronized (sCostLock) {
                try {
                    if (this.mIsDebugCostTime >= 5 && cost > this.mIsDebugCostTime) {
                        Map<String, PropertyPermissionInfo> propertyPermissions = this.mCostPackageMaps.get(packageName);
                        if (propertyPermissions == null) {
                            propertyPermissions = new ConcurrentHashMap();
                            this.mCostPackageMaps.put(packageName, propertyPermissions);
                        }
                        PropertyPermissionInfo info = propertyPermissions.get(propName);
                        if (info == null) {
                            try {
                                info = new PropertyPermissionInfo();
                                info.mPackageName = packageName;
                            } catch (Throwable th) {
                                th = th;
                            }
                            try {
                                info.mAccess = access;
                                info.mPropId = propId;
                                info.mPropName = propName;
                                info.mCost = cost;
                                info.mCostAvg = cost;
                                info.mCount = 1L;
                            } catch (Throwable th2) {
                                th = th2;
                                throw th;
                            }
                        } else {
                            info.mCost = cost;
                            info.mCostAvg = ((info.mCostAvg * info.mCount) + cost) / (info.mCount + 1);
                            info.mCount++;
                        }
                        propertyPermissions.put(propName, info);
                        Slog.i("CarPermissionManager", "saveCostPropInfo: check cost time info: " + info);
                    } else if (this.mIsDebugCostPropName.equals(propName)) {
                        Slog.i("CarPermissionManager", "saveCostPropInfo: " + propName + " check cost time=" + cost + " (" + costEnd + "," + costBegin + ")");
                    }
                } catch (Throwable th3) {
                    th = th3;
                }
            }
        }
    }

    public int getCarPermission(Context context, int uid, int pid, String packageName, int propId, String propName, int access) {
        int ret;
        logDebug(uid, pid, packageName, "check permission propName=" + propName);
        if (pid == Process.myPid()) {
            return 0;
        }
        int appFlag = getAppFlag(context, uid, pid, packageName);
        logDebug(uid, pid, packageName, "appFlag=" + appFlag);
        if (isWhiteList(uid, pid, packageName)) {
            logDebug(uid, pid, packageName, "white list app granted directly!");
            return 0;
        } else if ((this.mCheckAppFlag & appFlag) == 0 && !isBlackList(uid, pid, packageName)) {
            logDebug(uid, pid, packageName, "black list force checked!");
            return 0;
        } else if (TextUtils.isEmpty(propName)) {
            logDebug(uid, pid, packageName, new RuntimeException("propId:" + propId + " has empty propName."));
            return 5;
        } else if (appFlag == 4 && (ret = checkAllowed3rdAppId(context, uid, pid, packageName)) != 0) {
            return ret;
        } else {
            return checkAllowedPropId(uid, pid, packageName, propName, access);
        }
    }

    public void saveBindAppInfo(int uid, int pid, String packageName) {
        this.mCacheBindAppInfoMap.put(packageName, new BindAppInfo(uid, pid, packageName));
        this.mCacheBindPackages.put(Integer.valueOf(pid), packageName);
    }

    public void removeBindAppInfo(int uid, int pid, String packageName) {
        BindAppInfo info = this.mCacheBindAppInfoMap.remove(packageName);
        if (info != null) {
            this.mCacheBindPackages.remove(Integer.valueOf(info.mPid));
        }
    }

    private int getAppFlag(Context context, int uid, int pid, String packageName) {
        int appFlag;
        BindAppInfo info = this.mCacheBindAppInfoMap.get(packageName);
        if (info != null && info.mAppFlag != 0) {
            return info.mAppFlag;
        }
        if (checkAllowedUidGroup(uid, pid, packageName)) {
            appFlag = 1;
        } else if (checkSystemSignatures(context, uid, pid, packageName)) {
            appFlag = 2;
        } else {
            appFlag = 4;
        }
        if (info != null) {
            info.mAppFlag = appFlag;
        }
        return appFlag;
    }

    private boolean isBlackList(int uid, int pid, String packageName) {
        return getWhiteBlackType(uid, pid, packageName) == 2;
    }

    private boolean isWhiteList(int uid, int pid, String packageName) {
        return getWhiteBlackType(uid, pid, packageName) == 1;
    }

    private boolean isCheckCarPermission(int uid, int pid, String packageName) {
        int w_or_b = getWhiteBlackType(uid, pid, packageName);
        if (w_or_b == 2) {
            logDebug(uid, pid, packageName, "black list force checked!");
            return true;
        } else if (w_or_b == 1) {
            logDebug(uid, pid, packageName, "white list only sys app granted!");
            return false;
        } else {
            return this.mIsCarPermissionEnable;
        }
    }

    private int checkAllowed3rdAppId(Context context, int uid, int pid, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            logW(uid, pid, packageName, "is not java process!");
            return 1;
        }
        String apkAppId = getManifestAppId(context, packageName);
        if (TextUtils.isEmpty(apkAppId)) {
            logW(uid, pid, packageName, "appId is null!");
            return 2;
        }
        CarSignature signature = this.mCarSignatureMap.get(packageName);
        if (signature == null) {
            logW(uid, pid, packageName, " appId is not config!");
            return 5;
        }
        logDebug(uid, pid, packageName, "appId=" + apkAppId + "; target=" + signature.mAppId);
        if (!apkAppId.equals(signature.mAppId) && !apkAppId.equals(signature.mAppId2)) {
            logW(uid, pid, packageName, "appId is invalid!");
            return 3;
        }
        return 0;
    }

    private int checkAllowedPropId(int uid, int pid, String packageName, String propName, int access) {
        CarSignature signature = this.mCarSignatureMap.get(packageName);
        if (signature == null) {
            logW(uid, pid, packageName, "appId is not config!");
            return 5;
        }
        boolean isGranted = signature.checkPropAccess(propName, access);
        logDebug(uid, pid, packageName, "propName=" + propName + "; access=" + access + "; grant=" + isGranted);
        if (!isGranted) {
            return 8;
        }
        return 0;
    }

    @TargetApi(5)
    private boolean checkSystemSignatures(Context context, int uid, int pid, String packageName) {
        int res = this.mPackageManager.checkSignatures(Process.myUid(), uid);
        if (res != 0) {
            logDebug(uid, pid, packageName, "does not have the system signature! res=" + res);
            return false;
        }
        return true;
    }

    private boolean checkAllowedUidGroup(int uid, int pid, String packageName) {
        if (uid != 1000 && uid != 0 && uid != 1047 && uid != 1041) {
            return false;
        }
        logDebug(uid, pid, packageName, "is in target uid group.");
        return true;
    }

    private void logW(int uid, int pid, String packageName, String msg) {
        Slog.w("CarPermissionManager", "[" + uid + "," + pid + "," + packageName + "] " + msg);
    }

    private void logDebug(int uid, int pid, String packageName, String msg) {
        if (!this.mIsUser && !TextUtils.isEmpty(this.mDebugLogPkgName) && this.mDebugLogPkgName.equals(packageName)) {
            Slog.w("CarPermissionManager", "[" + uid + "," + pid + "," + packageName + "] " + msg);
        }
    }

    private void logDebug(int uid, int pid, String packageName, Throwable tr) {
        if (!this.mIsUser && !TextUtils.isEmpty(this.mDebugLogPkgName) && this.mDebugLogPkgName.equals(packageName)) {
            Slog.e("CarPermissionManager", "[" + uid + "," + pid + "," + packageName + "] ", tr);
        }
    }

    private void logDebug(String packageName, String msg) {
        if (!this.mIsUser && !TextUtils.isEmpty(this.mDebugLogPkgName) && this.mDebugLogPkgName.equals(packageName)) {
            Slog.w("CarPermissionManager", msg);
        }
    }
}
