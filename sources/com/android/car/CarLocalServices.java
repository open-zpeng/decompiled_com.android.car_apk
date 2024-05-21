package com.android.car;

import android.car.Car;
import android.car.ICar;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPower;
import android.content.Context;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.internal.annotations.VisibleForTesting;
/* loaded from: classes3.dex */
public class CarLocalServices {
    private static final ArrayMap<Class<?>, Object> sLocalServiceObjects = new ArrayMap<>();

    private CarLocalServices() {
    }

    public static <T> T getService(Class<T> type) {
        T t;
        Slog.d("CarLocalServices", " getService " + type.getSimpleName());
        synchronized (sLocalServiceObjects) {
            t = (T) sLocalServiceObjects.get(type);
        }
        return t;
    }

    public static <T> void addService(Class<T> type, T service) {
        synchronized (sLocalServiceObjects) {
            if (sLocalServiceObjects.containsKey(type)) {
                throw new IllegalStateException("Overriding service registration");
            }
            Slog.d("CarLocalServices", " Adding " + type.getSimpleName());
            sLocalServiceObjects.put(type, service);
        }
    }

    @VisibleForTesting
    public static <T> void removeServiceForTest(Class<T> type) {
        Slog.d("CarLocalServices", " Removing " + type.getSimpleName());
        synchronized (sLocalServiceObjects) {
            sLocalServiceObjects.remove(type);
        }
    }

    public static void removeAllServices() {
        Slog.d("CarLocalServices", " removeAllServices");
        synchronized (sLocalServiceObjects) {
            sLocalServiceObjects.clear();
        }
    }

    public static CarPowerManager createCarPowerManager(Context context) {
        Car car = new Car(context, (ICar) null, (Handler) null);
        ICarPower.Stub stub = (CarPowerManagementService) getService(CarPowerManagementService.class);
        if (stub == null) {
            return null;
        }
        return new CarPowerManager(car, stub);
    }
}
