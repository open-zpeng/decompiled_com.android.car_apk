package com.android.car;

import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.drivingstate.ICarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayAddress;
import androidx.core.app.FrameMetricsAggregator;
import com.android.car.Manifest;
import com.android.car.Utils;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
public class CarUxRestrictionsManagerService extends ICarUxRestrictionsManager.Stub implements CarServiceBase {
    @VisibleForTesting
    static final String CONFIG_FILENAME_PRODUCTION = "ux_restrictions_prod_config.json";
    @VisibleForTesting
    static final String CONFIG_FILENAME_STAGED = "ux_restrictions_staged_config.json";
    private static final boolean DBG = false;
    private static final byte DEFAULT_PORT = 0;
    private static final String JSON_NAME_RESTRICTIONS = "restrictions";
    private static final String JSON_NAME_SCHEMA_VERSION = "schema_version";
    private static final int JSON_SCHEMA_VERSION_V1 = 1;
    private static final int JSON_SCHEMA_VERSION_V2 = 2;
    private static final int MAX_TRANSITION_LOG_SIZE = 20;
    private static final int PROPERTY_UPDATE_RATE = 5;
    private static final float SPEED_NOT_AVAILABLE = -1.0f;
    private static final String TAG = "CarUxR";
    private static final int UNKNOWN_JSON_SCHEMA_VERSION = -1;
    private final CarPropertyService mCarPropertyService;
    private Map<Byte, CarUxRestrictionsConfiguration> mCarUxRestrictionsConfigurations;
    private final Context mContext;
    private float mCurrentMovingSpeed;
    private Map<Byte, CarUxRestrictions> mCurrentUxRestrictions;
    private byte mDefaultDisplayPhysicalPort;
    private final DisplayManager mDisplayManager;
    private final CarDrivingStateService mDrivingStateService;
    private final List<UxRestrictionsClient> mUxRClients = new ArrayList();
    private final List<Byte> mPhysicalPorts = new ArrayList();
    private final Map<Integer, Byte> mPortLookup = new HashMap();
    private String mRestrictionMode = "baseline";
    @GuardedBy({"this"})
    private boolean mUxRChangeBroadcastEnabled = true;
    private final LinkedList<Utils.TransitionLog> mTransitionLogs = new LinkedList<>();
    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener = new ICarDrivingStateChangeListener.Stub() { // from class: com.android.car.CarUxRestrictionsManagerService.1
        public void onDrivingStateChanged(CarDrivingStateEvent event) {
            CarUxRestrictionsManagerService.logd("Driving State Changed:" + event.eventValue);
            CarUxRestrictionsManagerService.this.handleDrivingStateEvent(event);
        }
    };
    private final ICarPropertyEventListener mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.CarUxRestrictionsManagerService.2
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            for (CarPropertyEvent event : events) {
                if (event.getEventType() == 0 && event.getCarPropertyValue().getPropertyId() == 291504647) {
                    CarUxRestrictionsManagerService.this.handleSpeedChange(((Float) event.getCarPropertyValue().getValue()).floatValue());
                }
            }
        }
    };

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    private @interface JsonSchemaVersion {
    }

    public CarUxRestrictionsManagerService(Context context, CarDrivingStateService drvService, CarPropertyService propertyService) {
        this.mContext = context;
        this.mDisplayManager = (DisplayManager) this.mContext.getSystemService(DisplayManager.class);
        this.mDrivingStateService = drvService;
        this.mCarPropertyService = propertyService;
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void init() {
        this.mDefaultDisplayPhysicalPort = getDefaultDisplayPhysicalPort();
        initPhysicalPort();
        this.mCurrentUxRestrictions = new HashMap();
        for (Byte b : this.mPhysicalPorts) {
            byte port = b.byteValue();
            this.mCurrentUxRestrictions.put(Byte.valueOf(port), createUnrestrictedRestrictions());
        }
        this.mCarUxRestrictionsConfigurations = convertToMap(loadConfig());
        this.mDrivingStateService.registerDrivingStateChangeListener(this.mICarDrivingStateChangeEventListener);
        this.mCarPropertyService.registerListener(VehicleProperty.PERF_VEHICLE_SPEED, 5.0f, this.mICarPropertyEventListener);
        initializeUxRestrictions();
    }

    public List<CarUxRestrictionsConfiguration> getConfigs() {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_UX_RESTRICTIONS_CONFIGURATION);
        return new ArrayList(this.mCarUxRestrictionsConfigurations.values());
    }

    @VisibleForTesting
    synchronized List<CarUxRestrictionsConfiguration> loadConfig() {
        promoteStagedConfig();
        File prodConfig = getFile(CONFIG_FILENAME_PRODUCTION);
        if (prodConfig.exists()) {
            logd("Attempting to read production config");
            List<CarUxRestrictionsConfiguration> configs = readPersistedConfig(prodConfig);
            if (configs != null) {
                return configs;
            }
        }
        logd("Attempting to read config from XML resource");
        List<CarUxRestrictionsConfiguration> configs2 = readXmlConfig();
        if (configs2 != null) {
            return configs2;
        }
        Slog.w(TAG, "Creating default config");
        List<CarUxRestrictionsConfiguration> configs3 = new ArrayList<>();
        for (Byte b : this.mPhysicalPorts) {
            byte port = b.byteValue();
            configs3.add(createDefaultConfig(port));
        }
        return configs3;
    }

    private File getFile(String filename) {
        SystemInterface systemInterface = (SystemInterface) CarLocalServices.getService(SystemInterface.class);
        return new File(systemInterface.getSystemCarDir(), filename);
    }

    private List<CarUxRestrictionsConfiguration> readXmlConfig() {
        try {
            return CarUxRestrictionsConfigurationXmlParser.parse(this.mContext, R.xml.car_ux_restrictions_map);
        } catch (IOException | XmlPullParserException e) {
            Slog.e(TAG, "Could not read config from XML resource", e);
            return null;
        }
    }

    private void promoteStagedConfig() {
        Path stagedConfig = getFile(CONFIG_FILENAME_STAGED).toPath();
        CarDrivingStateEvent currentDrivingStateEvent = this.mDrivingStateService.getCurrentDrivingState();
        if (currentDrivingStateEvent != null && currentDrivingStateEvent.eventValue == 0 && Files.exists(stagedConfig, new LinkOption[0])) {
            Path prod = getFile(CONFIG_FILENAME_PRODUCTION).toPath();
            try {
                logd("Attempting to promote stage config");
                Files.move(stagedConfig, prod, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Slog.e(TAG, "Could not promote state config", e);
            }
        }
    }

    private void initializeUxRestrictions() {
        CarDrivingStateEvent currentDrivingStateEvent = this.mDrivingStateService.getCurrentDrivingState();
        if (currentDrivingStateEvent == null || currentDrivingStateEvent.eventValue == -1) {
            return;
        }
        int currentDrivingState = currentDrivingStateEvent.eventValue;
        Float currentSpeed = getCurrentSpeed();
        if (currentSpeed.floatValue() == SPEED_NOT_AVAILABLE) {
            return;
        }
        handleDispatchUxRestrictions(currentDrivingState, currentSpeed.floatValue());
    }

    private Float getCurrentSpeed() {
        CarPropertyValue value = this.mCarPropertyService.getProperty(VehicleProperty.PERF_VEHICLE_SPEED, 0);
        if (value != null) {
            return (Float) value.getValue();
        }
        return Float.valueOf((float) SPEED_NOT_AVAILABLE);
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
        for (UxRestrictionsClient client : this.mUxRClients) {
            client.listenerBinder.unlinkToDeath(client, 0);
        }
        this.mUxRClients.clear();
        this.mDrivingStateService.unregisterDrivingStateChangeListener(this.mICarDrivingStateChangeEventListener);
    }

    public synchronized void registerUxRestrictionsChangeListener(ICarUxRestrictionsChangeListener listener, int displayId) {
        if (listener == null) {
            Slog.e(TAG, "registerUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        } else if (findUxRestrictionsClient(listener) == null) {
            UxRestrictionsClient client = new UxRestrictionsClient(listener, displayId);
            try {
                listener.asBinder().linkToDeath(client, 0);
            } catch (RemoteException e) {
                Slog.e(TAG, "Cannot link death recipient to binder " + e);
            }
            this.mUxRClients.add(client);
        }
    }

    private UxRestrictionsClient findUxRestrictionsClient(ICarUxRestrictionsChangeListener listener) {
        IBinder binder = listener.asBinder();
        for (UxRestrictionsClient client : this.mUxRClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    public synchronized void unregisterUxRestrictionsChangeListener(ICarUxRestrictionsChangeListener listener) {
        if (listener == null) {
            Slog.e(TAG, "unregisterUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }
        UxRestrictionsClient client = findUxRestrictionsClient(listener);
        if (client == null) {
            Slog.e(TAG, "unregisterUxRestrictionsChangeListener(): listener was not previously registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        this.mUxRClients.remove(client);
    }

    public synchronized CarUxRestrictions getCurrentUxRestrictions(int displayId) {
        CarUxRestrictions restrictions;
        restrictions = this.mCurrentUxRestrictions.get(getPhysicalPort(displayId));
        if (restrictions == null) {
            Slog.e(TAG, String.format("Restrictions are null for displayId:%d. Returning full restrictions.", Integer.valueOf(displayId)));
            restrictions = createFullyRestrictedRestrictions();
        }
        return restrictions;
    }

    public synchronized CarUxRestrictions getCurrentUxRestrictions() {
        return getCurrentUxRestrictions(0);
    }

    public synchronized boolean saveUxRestrictionsConfigurationForNextBoot(List<CarUxRestrictionsConfiguration> configs) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_UX_RESTRICTIONS_CONFIGURATION);
        validateConfigs(configs);
        return persistConfig(configs, CONFIG_FILENAME_STAGED);
    }

    public List<CarUxRestrictionsConfiguration> getStagedConfigs() {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_UX_RESTRICTIONS_CONFIGURATION);
        File stagedConfig = getFile(CONFIG_FILENAME_STAGED);
        if (stagedConfig.exists()) {
            logd("Attempting to read staged config");
            return readPersistedConfig(stagedConfig);
        }
        return null;
    }

    public synchronized boolean setRestrictionMode(String mode) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_UX_RESTRICTIONS_CONFIGURATION);
        Objects.requireNonNull(mode, "mode must not be null");
        if (this.mRestrictionMode.equals(mode)) {
            return true;
        }
        addTransitionLog(TAG, this.mRestrictionMode, mode, System.currentTimeMillis(), "Restriction mode");
        this.mRestrictionMode = mode;
        logd("Set restriction mode to: " + mode);
        handleDispatchUxRestrictions(this.mDrivingStateService.getCurrentDrivingState().eventValue, getCurrentSpeed().floatValue());
        return true;
    }

    public synchronized String getRestrictionMode() {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_UX_RESTRICTIONS_CONFIGURATION);
        return this.mRestrictionMode;
    }

    private boolean persistConfig(List<CarUxRestrictionsConfiguration> configs, String filename) {
        File file = getFile(filename);
        AtomicFile stagedFile = new AtomicFile(file);
        try {
            FileOutputStream fos = stagedFile.startWrite();
            try {
                JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
                jsonWriter.beginObject();
                jsonWriter.name(JSON_NAME_SCHEMA_VERSION).value(2L);
                jsonWriter.name(JSON_NAME_RESTRICTIONS);
                jsonWriter.beginArray();
                for (CarUxRestrictionsConfiguration config : configs) {
                    config.writeJson(jsonWriter);
                }
                jsonWriter.endArray();
                jsonWriter.endObject();
                $closeResource(null, jsonWriter);
                stagedFile.finishWrite(fos);
                return true;
            } catch (IOException e) {
                Slog.e(TAG, "Could not persist config", e);
                stagedFile.failWrite(fos);
                return false;
            }
        } catch (IOException e2) {
            Slog.e(TAG, "Could not open file to persist config", e2);
            return false;
        }
    }

    private static /* synthetic */ void $closeResource(Throwable x0, AutoCloseable x1) {
        if (x0 == null) {
            x1.close();
            return;
        }
        try {
            x1.close();
        } catch (Throwable th) {
            x0.addSuppressed(th);
        }
    }

    private List<CarUxRestrictionsConfiguration> readPersistedConfig(File file) {
        if (!file.exists()) {
            Slog.e(TAG, "Could not find config file: " + file.getName());
            return null;
        }
        int schemaVersion = readFileSchemaVersion(file);
        AtomicFile configFile = new AtomicFile(file);
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(configFile.openRead(), StandardCharsets.UTF_8));
            List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
            if (schemaVersion == 1) {
                readV1Json(reader, configs);
            } else if (schemaVersion == 2) {
                readV2Json(reader, configs);
            } else {
                Slog.e(TAG, "Unable to parse schema for version " + schemaVersion);
            }
            $closeResource(null, reader);
            return configs;
        } catch (IOException e) {
            Slog.e(TAG, "Could not read persisted config file " + file.getName(), e);
            return null;
        }
    }

    private void readV1Json(JsonReader reader, List<CarUxRestrictionsConfiguration> configs) throws IOException {
        readRestrictionsArray(reader, configs, 1);
    }

    private void readV2Json(JsonReader reader, List<CarUxRestrictionsConfiguration> configs) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            char c = 65535;
            if (name.hashCode() == -1148295641 && name.equals(JSON_NAME_RESTRICTIONS)) {
                c = 0;
            }
            if (c == 0) {
                readRestrictionsArray(reader, configs, 2);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private int readFileSchemaVersion(File file) {
        JsonReader reader;
        AtomicFile configFile = new AtomicFile(file);
        try {
            reader = new JsonReader(new InputStreamReader(configFile.openRead(), StandardCharsets.UTF_8));
            new ArrayList();
        } catch (IOException e) {
            Slog.e(TAG, "Could not read persisted config file " + file.getName(), e);
        }
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.close();
            $closeResource(null, reader);
            return 1;
        }
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!((name.hashCode() == 1684719674 && name.equals(JSON_NAME_SCHEMA_VERSION)) ? false : true)) {
                int schemaVersion = reader.nextInt();
                reader.close();
                $closeResource(null, reader);
                return schemaVersion;
            }
            reader.skipValue();
        }
        reader.endObject();
        $closeResource(null, reader);
        return -1;
    }

    private void readRestrictionsArray(JsonReader reader, List<CarUxRestrictionsConfiguration> configs, int schemaVersion) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            configs.add(CarUxRestrictionsConfiguration.readJson(reader, schemaVersion));
        }
        reader.endArray();
    }

    public synchronized void setUxRChangeBroadcastEnabled(boolean enable) {
        if (!isDebugBuild()) {
            Slog.e(TAG, "Cannot set UX restriction change broadcast.");
        } else if (this.mContext.getPackageManager().checkSignatures(Process.myUid(), Binder.getCallingUid()) != 0) {
            throw new SecurityException("Caller " + this.mContext.getPackageManager().getNameForUid(Binder.getCallingUid()) + " does not have the right signature");
        } else {
            if (enable) {
                this.mUxRChangeBroadcastEnabled = enable;
                handleDispatchUxRestrictions(this.mDrivingStateService.getCurrentDrivingState().eventValue, getCurrentSpeed().floatValue());
            } else {
                handleDispatchUxRestrictions(0, 0.0f);
                this.mUxRChangeBroadcastEnabled = enable;
            }
        }
    }

    private boolean isDebugBuild() {
        return Build.IS_USERDEBUG || Build.IS_ENG;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class UxRestrictionsClient implements IBinder.DeathRecipient {
        private final ICarUxRestrictionsChangeListener listener;
        private final IBinder listenerBinder;
        private final int mDisplayId;

        UxRestrictionsClient(ICarUxRestrictionsChangeListener l, int displayId) {
            this.listener = l;
            this.listenerBinder = l.asBinder();
            this.mDisplayId = displayId;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            CarUxRestrictionsManagerService.logd("Binder died " + this.listenerBinder);
            this.listenerBinder.unlinkToDeath(this, 0);
            synchronized (CarUxRestrictionsManagerService.this) {
                CarUxRestrictionsManagerService.this.mUxRClients.remove(this);
            }
        }

        public boolean isHoldingBinder(IBinder binder) {
            return this.listenerBinder == binder;
        }

        public void dispatchEventToClients(CarUxRestrictions event) {
            if (event == null) {
                return;
            }
            try {
                this.listener.onUxRestrictionsChanged(event);
            } catch (RemoteException e) {
                Slog.e(CarUxRestrictionsManagerService.TAG, "Dispatch to listener failed", e);
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*CarUxRestrictionsManagerService*");
        for (Byte b : this.mCurrentUxRestrictions.keySet()) {
            byte port = b.byteValue();
            CarUxRestrictions restrictions = this.mCurrentUxRestrictions.get(Byte.valueOf(port));
            writer.printf("Port: 0x%02X UXR: %s\n", Byte.valueOf(port), restrictions.toString());
        }
        if (isDebugBuild()) {
            writer.println("mUxRChangeBroadcastEnabled? " + this.mUxRChangeBroadcastEnabled);
        }
        writer.println("UX Restriction configurations:");
        for (CarUxRestrictionsConfiguration config : this.mCarUxRestrictionsConfigurations.values()) {
            config.dump(writer);
        }
        writer.println("UX Restriction change log:");
        Iterator<Utils.TransitionLog> it = this.mTransitionLogs.iterator();
        while (it.hasNext()) {
            Utils.TransitionLog tlog = it.next();
            writer.println(tlog);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void handleDrivingStateEvent(CarDrivingStateEvent event) {
        if (event == null) {
            return;
        }
        int drivingState = event.eventValue;
        Float speed = getCurrentSpeed();
        if (speed.floatValue() != SPEED_NOT_AVAILABLE) {
            this.mCurrentMovingSpeed = speed.floatValue();
        } else {
            if (drivingState != 0 && drivingState != -1) {
                Slog.e(TAG, "Unexpected:  Speed null when driving state is: " + drivingState);
                return;
            }
            logd("Speed null when driving state is: " + drivingState);
            this.mCurrentMovingSpeed = 0.0f;
        }
        handleDispatchUxRestrictions(drivingState, this.mCurrentMovingSpeed);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void handleSpeedChange(float newSpeed) {
        if (Float.compare(newSpeed, this.mCurrentMovingSpeed) == 0) {
            return;
        }
        int currentDrivingState = this.mDrivingStateService.getCurrentDrivingState().eventValue;
        if (currentDrivingState != 2) {
            return;
        }
        this.mCurrentMovingSpeed = newSpeed;
        handleDispatchUxRestrictions(currentDrivingState, newSpeed);
    }

    private synchronized void handleDispatchUxRestrictions(int currentDrivingState, float speed) {
        Preconditions.checkNotNull(this.mCarUxRestrictionsConfigurations, "mCarUxRestrictionsConfigurations must be initialized");
        Preconditions.checkNotNull(this.mCurrentUxRestrictions, "mCurrentUxRestrictions must be initialized");
        if (isDebugBuild() && !this.mUxRChangeBroadcastEnabled) {
            Slog.d(TAG, "Not dispatching UX Restriction due to setting");
            return;
        }
        Map<Byte, CarUxRestrictions> newUxRestrictions = new HashMap<>();
        for (Byte b : this.mPhysicalPorts) {
            byte port = b.byteValue();
            CarUxRestrictionsConfiguration config = this.mCarUxRestrictionsConfigurations.get(Byte.valueOf(port));
            if (config != null) {
                CarUxRestrictions uxRestrictions = config.getUxRestrictions(currentDrivingState, speed, this.mRestrictionMode);
                logd(String.format("Display port 0x%02x\tDO old->new: %b -> %b", Byte.valueOf(port), Boolean.valueOf(this.mCurrentUxRestrictions.get(Byte.valueOf(port)).isRequiresDistractionOptimization()), Boolean.valueOf(uxRestrictions.isRequiresDistractionOptimization())));
                logd(String.format("Display port 0x%02x\tUxR old->new: 0x%x -> 0x%x", Byte.valueOf(port), Integer.valueOf(this.mCurrentUxRestrictions.get(Byte.valueOf(port)).getActiveRestrictions()), Integer.valueOf(uxRestrictions.getActiveRestrictions())));
                newUxRestrictions.put(Byte.valueOf(port), uxRestrictions);
            }
        }
        Set<Byte> displayToDispatch = new ArraySet<>();
        for (Byte b2 : newUxRestrictions.keySet()) {
            byte port2 = b2.byteValue();
            if (!this.mCurrentUxRestrictions.containsKey(Byte.valueOf(port2))) {
                Slog.e(TAG, "Unrecognized port:" + ((int) port2));
            } else if (!this.mCurrentUxRestrictions.get(Byte.valueOf(port2)).isSameRestrictions(newUxRestrictions.get(Byte.valueOf(port2)))) {
                displayToDispatch.add(Byte.valueOf(port2));
            }
        }
        if (displayToDispatch.isEmpty()) {
            return;
        }
        for (Byte b3 : displayToDispatch) {
            byte port3 = b3.byteValue();
            addTransitionLog(this.mCurrentUxRestrictions.get(Byte.valueOf(port3)), newUxRestrictions.get(Byte.valueOf(port3)));
        }
        logd("dispatching to clients");
        for (UxRestrictionsClient client : this.mUxRClients) {
            Byte clientDisplayPort = getPhysicalPort(client.mDisplayId);
            if (clientDisplayPort == null) {
                clientDisplayPort = Byte.valueOf(this.mDefaultDisplayPhysicalPort);
            }
            if (displayToDispatch.contains(clientDisplayPort)) {
                client.dispatchEventToClients(newUxRestrictions.get(clientDisplayPort));
            }
        }
        this.mCurrentUxRestrictions = newUxRestrictions;
    }

    private byte getDefaultDisplayPhysicalPort() {
        Display defaultDisplay = this.mDisplayManager.getDisplay(0);
        DisplayAddress.Physical address = defaultDisplay.getAddress();
        if (address == null) {
            Slog.w(TAG, "Default display does not have physical display port.");
            return (byte) 0;
        }
        return address.getPort();
    }

    private void initPhysicalPort() {
        Display[] displays;
        for (Display display : this.mDisplayManager.getDisplays()) {
            if (display.getType() != 5) {
                if (display.getDisplayId() == 0 && display.getAddress() == null) {
                    if (Log.isLoggable(TAG, 4)) {
                        Slog.i(TAG, "Default display does not have display address. Using default.");
                    }
                    this.mPhysicalPorts.add(Byte.valueOf(this.mDefaultDisplayPhysicalPort));
                } else if (display.getAddress() instanceof DisplayAddress.Physical) {
                    byte port = display.getAddress().getPort();
                    if (Log.isLoggable(TAG, 4)) {
                        Slog.i(TAG, String.format("Display %d uses port %d", Integer.valueOf(display.getDisplayId()), Byte.valueOf(port)));
                    }
                    this.mPhysicalPorts.add(Byte.valueOf(port));
                } else {
                    Slog.w(TAG, "At init non-virtual display has a non-physical display address: " + display);
                }
            }
        }
    }

    private Map<Byte, CarUxRestrictionsConfiguration> convertToMap(List<CarUxRestrictionsConfiguration> configs) {
        byte port;
        validateConfigs(configs);
        Map<Byte, CarUxRestrictionsConfiguration> result = new HashMap<>();
        if (configs.size() == 1) {
            CarUxRestrictionsConfiguration config = configs.get(0);
            if (config.getPhysicalPort() == null) {
                port = this.mDefaultDisplayPhysicalPort;
            } else {
                port = config.getPhysicalPort().byteValue();
            }
            result.put(Byte.valueOf(port), config);
        } else {
            for (CarUxRestrictionsConfiguration config2 : configs) {
                result.put(config2.getPhysicalPort(), config2);
            }
        }
        return result;
    }

    @VisibleForTesting
    void validateConfigs(List<CarUxRestrictionsConfiguration> configs) {
        if (configs.size() == 0) {
            throw new IllegalArgumentException("Empty configuration.");
        }
        if (configs.size() == 1) {
            return;
        }
        CarUxRestrictionsConfiguration first = configs.get(0);
        Set<Byte> existingPorts = new ArraySet<>();
        for (CarUxRestrictionsConfiguration config : configs) {
            if (!config.hasSameParameters(first)) {
                throw new IllegalArgumentException("Configurations should have the same restrictions parameters.");
            }
            Byte port = config.getPhysicalPort();
            if (port == null) {
                throw new IllegalArgumentException("Input contains multiple configurations; each must set physical port.");
            }
            if (existingPorts.contains(port)) {
                throw new IllegalArgumentException("Multiple configurations for port " + port);
            }
            existingPorts.add(port);
        }
    }

    private Byte getPhysicalPort(int displayId) {
        if (!this.mPortLookup.containsKey(Integer.valueOf(displayId))) {
            Display display = this.mDisplayManager.getDisplay(displayId);
            if (display == null) {
                Slog.w(TAG, "Could not retrieve display for id: " + displayId);
                return null;
            }
            byte port = getPhysicalPort(display);
            this.mPortLookup.put(Integer.valueOf(displayId), Byte.valueOf(port));
        }
        return this.mPortLookup.get(Integer.valueOf(displayId));
    }

    private byte getPhysicalPort(Display display) {
        if (display.getType() == 5) {
            return this.mDefaultDisplayPhysicalPort;
        }
        DisplayAddress.Physical address = display.getAddress();
        if (address == null) {
            Slog.e(TAG, "Display " + display + " is not a virtual display but has null DisplayAddress.");
            return this.mDefaultDisplayPhysicalPort;
        } else if (!(address instanceof DisplayAddress.Physical)) {
            Slog.e(TAG, "Display " + display + " has non-physical address: " + address);
            return this.mDefaultDisplayPhysicalPort;
        } else {
            return address.getPort();
        }
    }

    private CarUxRestrictions createUnrestrictedRestrictions() {
        return new CarUxRestrictions.Builder(false, 0, SystemClock.elapsedRealtimeNanos()).build();
    }

    private CarUxRestrictions createFullyRestrictedRestrictions() {
        return new CarUxRestrictions.Builder(true, (int) FrameMetricsAggregator.EVERY_DURATION, SystemClock.elapsedRealtimeNanos()).build();
    }

    CarUxRestrictionsConfiguration createDefaultConfig(byte port) {
        return new CarUxRestrictionsConfiguration.Builder().setPhysicalPort(port).setUxRestrictions(0, false, 0).setUxRestrictions(1, false, 0).setUxRestrictions(2, true, (int) FrameMetricsAggregator.EVERY_DURATION).setUxRestrictions(-1, true, (int) FrameMetricsAggregator.EVERY_DURATION).build();
    }

    private void addTransitionLog(String name, String from, String to, long timestamp, String extra) {
        if (this.mTransitionLogs.size() >= 20) {
            this.mTransitionLogs.remove();
        }
        Utils.TransitionLog tLog = new Utils.TransitionLog(name, from, to, timestamp, extra);
        this.mTransitionLogs.add(tLog);
    }

    private void addTransitionLog(CarUxRestrictions oldRestrictions, CarUxRestrictions newRestrictions) {
        if (this.mTransitionLogs.size() >= 20) {
            this.mTransitionLogs.remove();
        }
        StringBuilder extra = new StringBuilder();
        extra.append(oldRestrictions.isRequiresDistractionOptimization() ? "DO -> " : "No DO -> ");
        extra.append(newRestrictions.isRequiresDistractionOptimization() ? "DO" : "No DO");
        Utils.TransitionLog tLog = new Utils.TransitionLog(TAG, Integer.valueOf(oldRestrictions.getActiveRestrictions()), Integer.valueOf(newRestrictions.getActiveRestrictions()), System.currentTimeMillis(), extra.toString());
        this.mTransitionLogs.add(tLog);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logd(String msg) {
    }
}
