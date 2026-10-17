package com.gmc1987.chronos.mobile.app.navigation;

import android.app.Activity;
import android.graphics.Color;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.R;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.RemoteListScreen;
import com.gmc1987.chronos.mobile.core.common.RowFormatters;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.model.PortalState;
import com.gmc1987.chronos.mobile.core.network.SessionStore;
import com.gmc1987.chronos.mobile.feature.announcements.AnnouncementsRepository;
import com.gmc1987.chronos.mobile.feature.applications.ApplicationsScreen;
import com.gmc1987.chronos.mobile.feature.auth.AuthRepository;
import com.gmc1987.chronos.mobile.feature.exams.ExamsRepository;
import com.gmc1987.chronos.mobile.feature.grades.GradesRepository;
import com.gmc1987.chronos.mobile.feature.profile.ProfileScreen;
import com.gmc1987.chronos.mobile.feature.schedule.ScheduleRepository;
import com.gmc1987.chronos.mobile.feature.schedule.CourseDetailScreen;
import com.gmc1987.chronos.mobile.feature.applications.WebPortalScreen;
import com.gmc1987.chronos.mobile.feature.workbench.MeetingsRepository;
import com.gmc1987.chronos.mobile.feature.workbench.SupervisionRepository;
import com.gmc1987.chronos.mobile.feature.workbench.WorkbenchScreen;

public final class AppNavigator implements ScreenHost {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final PortalState portal;
    private final AuthRepository auth;
    private final ScheduleRepository schedules;
    private final GradesRepository grades;
    private final ExamsRepository exams;
    private final AnnouncementsRepository announcements;
    private final MeetingsRepository meetings;
    private final SupervisionRepository supervision;
    private final SessionStore session;
    private final Runnable loggedOut;
    private FrameLayout content;
    private LinearLayout tabs;
    private String selectedTab = "工作台";

    public AppNavigator(
            Activity activity,
            UiKit ui,
            AsyncRunner async,
            PortalState portal,
            AuthRepository auth,
            ScheduleRepository schedules,
            GradesRepository grades,
            ExamsRepository exams,
            AnnouncementsRepository announcements,
            MeetingsRepository meetings,
            SupervisionRepository supervision,
            SessionStore session,
            Runnable loggedOut) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.portal = portal;
        this.auth = auth;
        this.schedules = schedules;
        this.grades = grades;
        this.exams = exams;
        this.announcements = announcements;
        this.meetings = meetings;
        this.supervision = supervision;
        this.session = session;
        this.loggedOut = loggedOut;
    }

    public void start() {
        LinearLayout shell = ui.vertical();
        shell.setBackgroundColor(UiKit.BACKGROUND);
        content = new FrameLayout(activity);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        tabs = ui.horizontal();
        tabs.setPadding(ui.dp(6), ui.dp(7), ui.dp(6), ui.dp(7));
        tabs.setBackgroundColor(Color.WHITE);
        tabs.setElevation(ui.dp(10));
        shell.addView(tabs);
        activity.setContentView(shell);
        renderTabs();
        showWorkbench();
    }

    @Override
    public void show(android.view.View view) {
        content.removeAllViews();
        content.addView(view);
    }

    private void renderTabs() {
        tabs.removeAllViews();
        addTab("工作台", R.drawable.ic_tab_workbench, this::showWorkbench);
        if (portal.workspace() == PortalState.Workspace.STUDENT) {
            addTab("成绩", R.drawable.ic_tab_grades, this::showGrades);
            addTab("考试", R.drawable.ic_tab_exams, this::showExams);
            addTab("通知", R.drawable.ic_tab_notifications,
                    () -> showAnnouncements("通知", "当前没有可见通知"));
        } else {
            addTab("应用", R.drawable.ic_tab_applications, this::showApplications);
            addTab("公告", R.drawable.ic_tab_announcements,
                    () -> showAnnouncements("公告", "当前没有可见公告"));
        }
        addTab("我的", R.drawable.ic_tab_profile, this::showProfile);
    }

    private void addTab(String title, int iconResource, Runnable action) {
        Button button = ui.tabButton(title, iconResource, title.equals(selectedTab));
        button.setOnClickListener(view -> {
            selectedTab = title;
            renderTabs();
            action.run();
        });
        tabs.addView(button, new LinearLayout.LayoutParams(0, ui.dp(60), 1));
    }

    private void showWorkbench() {
        new WorkbenchScreen(ui, async, this,
                activity.getSharedPreferences("chronos_session", Activity.MODE_PRIVATE),
                portal, schedules, meetings, supervision, this::showApplications,
                this::showCourseDetail).show();
    }

    private void showApplications() {
        new ApplicationsScreen(ui, this, portal, this::openApplication).show();
    }

    private void showProfile() {
        new ProfileScreen(ui, async, this, portal, auth, loggedOut).show();
    }

    private void showGrades() {
        remote().show("成绩", "当前学期暂无已发布成绩", grades::list, RowFormatters::grades);
    }

    private void showExams() {
        remote().show("考试", "近期没有考试安排", exams::list, RowFormatters::exams);
    }

    private void showAnnouncements(String title, String empty) {
        remote().show(title, empty, announcements::list, RowFormatters::publications);
    }

    private void openApplication(org.json.JSONObject application) {
        new WebPortalScreen(activity, ui, this, session, application, this::showApplications).show();
    }

    private void showCourseDetail(org.json.JSONObject occurrence) {
        new CourseDetailScreen(ui, this, occurrence, this::showWorkbench).show();
    }

    private RemoteListScreen remote() {
        return new RemoteListScreen(ui, async, this);
    }
}
