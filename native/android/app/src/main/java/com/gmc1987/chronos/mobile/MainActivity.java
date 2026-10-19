package com.gmc1987.chronos.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.app.navigation.AppNavigator;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.DocumentPicker;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import com.gmc1987.chronos.mobile.core.network.SessionStore;
import com.gmc1987.chronos.mobile.feature.announcements.AnnouncementsRepository;
import com.gmc1987.chronos.mobile.feature.auth.AuthRepository;
import com.gmc1987.chronos.mobile.feature.auth.LoginScreen;
import com.gmc1987.chronos.mobile.feature.exams.ExamsRepository;
import com.gmc1987.chronos.mobile.feature.grades.GradesRepository;
import com.gmc1987.chronos.mobile.feature.schedule.ScheduleRepository;
import com.gmc1987.chronos.mobile.feature.workbench.MeetingsRepository;
import com.gmc1987.chronos.mobile.feature.workbench.PortalRepository;
import com.gmc1987.chronos.mobile.feature.workbench.SupervisionRepository;

public final class MainActivity extends Activity implements ScreenHost {
    private static final String PREFS = "chronos_session";
    private UiKit ui;
    private AsyncRunner async;
    private AuthRepository auth;
    private PortalRepository portal;
    private HttpClient http;
    private SessionStore session;
    private AppNavigator navigator;
    private final DocumentPicker documentPicker = new DocumentPicker();
    private android.net.Uri pendingDeepLink;

    public DocumentPicker documentPicker() { return documentPicker; }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!documentPicker.handle(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new UiKit(this);
        async = new AsyncRunner(this);
        session = new SessionStore(getSharedPreferences(PREFS, MODE_PRIVATE));
        http = new HttpClient(BuildConfig.API_BASE_URL, session, getCacheDir());
        pendingDeepLink = getIntent() == null ? null : getIntent().getData();
        auth = new AuthRepository(http, session);
        portal = new PortalRepository(http);
        if (auth.hasSession()) initializePortal();
        else showLogin();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (navigator != null && intent.getData() != null) navigator.openDeepLink(intent.getData());
        else pendingDeepLink = intent.getData();
    }

    @Override
    public void onBackPressed() {
        if (navigator != null && navigator.navigateBack()) return;
        super.onBackPressed();
    }

    @Override
    public void show(android.view.View view) {
        setContentView(view);
    }

    private void showLogin() {
        navigator = null;
        new LoginScreen(ui, async, this, auth).show(this::initializePortal);
    }

    private void initializePortal() {
        show(ui.centered("正在初始化工作台…", true));
        async.run(portal::bootstrap, this::startApplication, this::showInitializationError);
    }

    private void startApplication(PortalState state) {
        navigator = new AppNavigator(
                this,
                ui,
                async,
                state,
                auth,
                new ScheduleRepository(http),
                new GradesRepository(http),
                new ExamsRepository(http),
                new AnnouncementsRepository(http),
                new MeetingsRepository(http),
                new SupervisionRepository(http),
                http,
                this::logoutToLogin);
        navigator.start();
        if (http.lastResponseWasCached()) {
            android.widget.Toast.makeText(this, "网络不可用，正在显示最近缓存",
                    android.widget.Toast.LENGTH_LONG).show();
        }
        if (pendingDeepLink != null) {
            navigator.openDeepLink(pendingDeepLink);
            pendingDeepLink = null;
        }
    }

    private void showInitializationError(Exception error) {
        LinearLayout page = ui.centered(
                ErrorMessages.display(error, "工作台初始化失败"), false);
        Button retry = ui.primaryButton("重新加载");
        Button logout = ui.secondaryButton("退出登录");
        retry.setOnClickListener(view -> initializePortal());
        logout.setOnClickListener(view -> {
            auth.clear();
            logoutToLogin();
        });
        page.addView(retry);
        page.addView(logout);
        show(page);
    }

    private void logoutToLogin() {
        if (http != null) http.clearCache();
        showLogin();
    }
}
