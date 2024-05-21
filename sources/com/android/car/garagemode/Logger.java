package com.android.car.garagemode;

import android.util.Slog;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class Logger {
    private final String mPrefix;
    private final String mTag = "GarageMode";

    /* JADX INFO: Access modifiers changed from: package-private */
    public Logger(String prefix) {
        this.mPrefix = prefix;
    }

    public void v(String msg) {
        Slog.v(this.mTag, buildMessage(msg));
    }

    public void v(String msg, Exception ex) {
        Slog.v(this.mTag, buildMessage(msg), ex);
    }

    public void i(String msg) {
        Slog.i(this.mTag, buildMessage(msg));
    }

    public void i(String msg, Exception ex) {
        Slog.i(this.mTag, buildMessage(msg), ex);
    }

    public void d(String msg) {
        Slog.d(this.mTag, buildMessage(msg));
    }

    public void d(String msg, Exception ex) {
        Slog.d(this.mTag, buildMessage(msg), ex);
    }

    public void w(String msg, Exception ex) {
        Slog.w(this.mTag, buildMessage(msg), ex);
    }

    public void w(String msg) {
        Slog.w(this.mTag, buildMessage(msg));
    }

    public void e(String msg) {
        Slog.e(this.mTag, buildMessage(msg));
    }

    public void e(String msg, Exception ex) {
        Slog.e(this.mTag, buildMessage(msg), ex);
    }

    public void e(String msg, Throwable ex) {
        Slog.e(this.mTag, buildMessage(msg), ex);
    }

    private String buildMessage(String msg) {
        return String.format("[%s]: %s", this.mPrefix, msg);
    }
}
