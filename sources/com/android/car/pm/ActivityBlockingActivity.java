package com.android.car.pm;

import android.app.Activity;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;
import com.android.car.CarLog;
import com.android.car.R;
/* loaded from: classes3.dex */
public class ActivityBlockingActivity extends Activity {
    private static final int INVALID_TASK_ID = -1;
    private int mBlockedTaskId;
    private Car mCar;
    private Button mExitButton;
    private final View.OnClickListener mOnExitButtonClickedListener = new View.OnClickListener() { // from class: com.android.car.pm.-$$Lambda$ActivityBlockingActivity$qEkZzGxYr4SHKHzP8soe-ty_MSY
        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            ActivityBlockingActivity.this.lambda$new$0$ActivityBlockingActivity(view);
        }
    };
    private final ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() { // from class: com.android.car.pm.ActivityBlockingActivity.1
        @Override // android.view.ViewTreeObserver.OnGlobalLayoutListener
        public void onGlobalLayout() {
            ActivityBlockingActivity.this.mToggleDebug.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            ActivityBlockingActivity.this.updateButtonWidths();
        }
    };
    private Button mToggleDebug;
    private CarUxRestrictionsManager mUxRManager;

    public /* synthetic */ void lambda$new$0$ActivityBlockingActivity(View v) {
        if (isExitOptionCloseApplication()) {
            handleCloseApplication();
        } else {
            handleRestartingTask();
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocking);
        this.mExitButton = (Button) findViewById(R.id.exit_button);
        this.mCar = Car.createCar(this, (Handler) null, -1L, new Car.CarServiceLifecycleListener() { // from class: com.android.car.pm.-$$Lambda$ActivityBlockingActivity$NDZGx4jpYENhpiDhFD2vDm24fDk
            public final void onLifecycleChanged(Car car, boolean z) {
                ActivityBlockingActivity.this.lambda$onCreate$1$ActivityBlockingActivity(car, z);
            }
        });
    }

    public /* synthetic */ void lambda$onCreate$1$ActivityBlockingActivity(Car car, boolean ready) {
        if (!ready) {
            return;
        }
        this.mUxRManager = (CarUxRestrictionsManager) car.getCarManager("uxrestriction");
        handleUxRChange(this.mUxRManager.getCurrentCarUxRestrictions());
        this.mUxRManager.registerListener(new CarUxRestrictionsManager.OnUxRestrictionsChangedListener() { // from class: com.android.car.pm.-$$Lambda$ActivityBlockingActivity$2aOjBy7l2ri8zXpoth3lxHlv12A
            public final void onUxRestrictionsChanged(CarUxRestrictions carUxRestrictions) {
                ActivityBlockingActivity.this.handleUxRChange(carUxRestrictions);
            }
        });
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mBlockedTaskId = getIntent().getIntExtra(CarPackageManagerService.BLOCKING_INTENT_EXTRA_BLOCKED_TASK_ID, -1);
        String blockedActivity = getIntent().getStringExtra(CarPackageManagerService.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
        if (!TextUtils.isEmpty(blockedActivity) && Log.isLoggable(CarLog.TAG_AM, 3)) {
            Slog.d(CarLog.TAG_AM, "Blocking activity " + blockedActivity);
        }
        displayExitButton();
        if (Build.IS_ENG || Build.IS_USERDEBUG) {
            displayDebugInfo();
        }
    }

    private void displayExitButton() {
        String exitButtonText = getExitButtonText();
        this.mExitButton.setText(exitButtonText);
        this.mExitButton.setOnClickListener(this.mOnExitButtonClickedListener);
    }

    private boolean isExitOptionCloseApplication() {
        boolean isRootDO = getIntent().getBooleanExtra(CarPackageManagerService.BLOCKING_INTENT_EXTRA_IS_ROOT_ACTIVITY_DO, false);
        return this.mBlockedTaskId == -1 || !isRootDO;
    }

    private String getExitButtonText() {
        return isExitOptionCloseApplication() ? getString(R.string.exit_button_close_application) : getString(R.string.exit_button_go_back);
    }

    private void displayDebugInfo() {
        String blockedActivity = getIntent().getStringExtra(CarPackageManagerService.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
        String rootActivity = getIntent().getStringExtra(CarPackageManagerService.BLOCKING_INTENT_EXTRA_ROOT_ACTIVITY_NAME);
        final TextView debugInfo = (TextView) findViewById(R.id.debug_info);
        debugInfo.setText(getDebugInfo(blockedActivity, rootActivity));
        this.mToggleDebug = (Button) findViewById(R.id.toggle_debug_info);
        this.mToggleDebug.setVisibility(0);
        this.mToggleDebug.setOnClickListener(new View.OnClickListener() { // from class: com.android.car.pm.-$$Lambda$ActivityBlockingActivity$PQjNhXqc0tsPz7-YtaUuKk1VFQw
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ActivityBlockingActivity.lambda$displayDebugInfo$2(debugInfo, view);
            }
        });
        this.mToggleDebug.getViewTreeObserver().addOnGlobalLayoutListener(this.mOnGlobalLayoutListener);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$displayDebugInfo$2(TextView debugInfo, View v) {
        boolean isDebugVisible = debugInfo.getVisibility() == 0;
        debugInfo.setVisibility(isDebugVisible ? 8 : 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateButtonWidths() {
        Button debugButton = (Button) findViewById(R.id.toggle_debug_info);
        int exitButtonWidth = this.mExitButton.getWidth();
        int debugButtonWidth = debugButton.getWidth();
        if (exitButtonWidth > debugButtonWidth) {
            debugButton.setWidth(exitButtonWidth);
        } else {
            this.mExitButton.setWidth(debugButtonWidth);
        }
    }

    private String getDebugInfo(String blockedActivity, String rootActivity) {
        StringBuilder debug = new StringBuilder();
        ComponentName blocked = ComponentName.unflattenFromString(blockedActivity);
        debug.append("Blocked activity is ");
        debug.append(blocked.getShortClassName());
        debug.append("\nBlocked activity package is ");
        debug.append(blocked.getPackageName());
        if (rootActivity != null) {
            ComponentName root = ComponentName.unflattenFromString(rootActivity);
            if (!root.equals(blocked)) {
                debug.append("\n\nRoot activity is ");
                debug.append(root.getShortClassName());
            }
            if (!root.getPackageName().equals(blocked.getPackageName())) {
                debug.append("\nRoot activity package is ");
                debug.append(root.getPackageName());
            }
        }
        return debug.toString();
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override // android.app.Activity
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        this.mUxRManager.unregisterListener();
        this.mToggleDebug.getViewTreeObserver().removeOnGlobalLayoutListener(this.mOnGlobalLayoutListener);
        this.mCar.disconnect();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleUxRChange(CarUxRestrictions restrictions) {
        if (restrictions != null && !restrictions.isRequiresDistractionOptimization()) {
            finish();
        }
    }

    private void handleCloseApplication() {
        if (isFinishing()) {
            return;
        }
        Intent startMain = new Intent("android.intent.action.MAIN");
        startMain.addCategory("android.intent.category.HOME");
        startMain.setFlags(268435456);
        startActivity(startMain);
        finish();
    }

    private void handleRestartingTask() {
        if (isFinishing()) {
            return;
        }
        synchronized (this) {
            if (Log.isLoggable(CarLog.TAG_AM, 4)) {
                Slog.i(CarLog.TAG_AM, "Restarting task " + this.mBlockedTaskId);
            }
            CarPackageManager carPm = (CarPackageManager) this.mCar.getCarManager("package");
            carPm.restartTask(this.mBlockedTaskId);
            finish();
        }
    }
}
