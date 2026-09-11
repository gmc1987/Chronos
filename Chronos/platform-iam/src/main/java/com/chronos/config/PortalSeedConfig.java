package com.chronos.config;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.chronos.Idao.IPortalApplicationRepository;
import com.chronos.Idao.IPortalWidgetRepository;
import com.chronos.model.pojo.PortalApplication;
import com.chronos.model.pojo.PortalWidget;

@Configuration
public class PortalSeedConfig {
    @Bean
    ApplicationRunner portalDefaults(IPortalApplicationRepository applications, IPortalWidgetRepository widgets) {
        return (ApplicationArguments args) -> {
            if (applications.count() == 0) {
                applications.saveAll(List.of(
                    app("workflow", "流程中心", "待办、申请与流程追踪", "List", "/portal/tasks", 10),
                    app("oa", "协同办公", "请假、出差、用车与用印", "OfficeBuilding", "/portal/apps", 20),
                    app("document", "公文中心", "收文、发文与公文查询", "Document", "/portal/apps", 30),
                    app("file", "文件中心", "文件上传、预览和权限管理", "Folder", "/portal/apps", 40),
                    app("knowledge", "知识中心", "制度、规范与知识检索", "Collection", "/portal/apps", 50),
                    app("ai", "AI 智能中心", "AI 助手与智能业务能力", "MagicStick", "/portal/apps", 60)
                ));
            }
            if (widgets.count() == 0) {
                widgets.saveAll(List.of(
                    widget("quick-entry", "快速入口", "QuickEntryWidget", "PORTAL", "LARGE", 10),
                    widget("todo", "我的待办", "TodoWidget", "WORKFLOW", "MEDIUM", 20),
                    widget("announcement", "公告", "AnnouncementWidget", "ANNOUNCEMENT", "MEDIUM", 30),
                    widget("notice", "通知", "NoticeWidget", "MESSAGE", "MEDIUM", 40),
                    widget("meeting", "近期会议", "MeetingWidget", "MEETING", "MEDIUM", 50),
                    widget("supervision", "督办事项", "SupervisionWidget", "SUPERVISION", "MEDIUM", 60),
                    widget("ai-assistant", "AI 助手", "AiAssistantWidget", "AI", "LARGE", 70),
                    widget("data-card", "数据概览", "DataCardWidget", "DATA", "LARGE", 80)
                ));
            }
			// Education widgets are upserted so existing installations receive them too.
			teacherWidget(widgets, "teacher-schedule", "我的课表", "TeacherScheduleWidget", "DATA", "LARGE", 15);
			teacherWidget(widgets, "teacher-workload", "我的工作量", "TeacherWorkloadWidget", "DATA", "MEDIUM", 25);
			teacherWidget(widgets, "teacher-leave", "我的请假/流程", "TeacherLeaveWidget", "DATA", "MEDIUM", 35);
			teacherWidget(widgets, "teacher-notifications", "通知摘要", "TeacherNotificationWidget", "DATA", "MEDIUM", 45);
			teacherWidget(widgets, "teacher-ai-analysis", "AI 教务分析", "TeacherAiAnalysisWidget", "DATA", "MEDIUM", 75);

			// 流程卡片只向具备流程实例查看权限的用户展示，兼容既有数据库中的种子记录。
			widgets.findByWidgetCode("todo").ifPresent(widget -> {
				widget.setRequiredPermission("workflow:instance:view");
				widgets.save(widget);
			});
			widgets.findByWidgetCode("supervision").ifPresent(widget -> {
				widget.setRequiredPermission("workflow:instance:view");
				widgets.save(widget);
			});
			widgets.findByWidgetCode("announcement").ifPresent(widget -> {
				widget.setRequiredPermission("message:publication:read");
				widgets.save(widget);
			});
			widgets.findByWidgetCode("notice").ifPresent(widget -> {
				widget.setRequiredPermission("message:publication:read");
				widgets.save(widget);
			});
        };
    }

	private void teacherWidget(IPortalWidgetRepository widgets, String code, String name, String component,
			String provider, String size, int order) {
		PortalWidget value = widgets.findByWidgetCode(code).orElseGet(PortalWidget::new);
		value.setWidgetCode(code); value.setWidgetName(name); value.setComponentName(component);
		value.setProviderCode(provider); value.setDefaultSize(size); value.setSortOrder(order);
		value.setEnabled(true); value.setAudienceRoleCodes("TEACHER");
		widgets.save(value);
	}

    private PortalApplication app(String code, String name, String description, String icon, String path, int order) {
        PortalApplication app = new PortalApplication(); app.setAppCode(code); app.setAppName(name);
        app.setDescription(description); app.setIcon(icon); app.setRoutePath(path); app.setOpenMode("INTERNAL");
        app.setSortOrder(order); app.setEnabled(true); app.setRecommended(order <= 30); return app;
    }

    private PortalWidget widget(String code, String name, String component, String provider, String size, int order) {
        PortalWidget widget = new PortalWidget(); widget.setWidgetCode(code); widget.setWidgetName(name);
        widget.setComponentName(component); widget.setProviderCode(provider); widget.setDefaultSize(size);
        widget.setSortOrder(order); widget.setEnabled(true); return widget;
    }
}
