package com.gmc1987.chronos.mobile;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class PortalStateTest {
    @Test
    public void studentProfileSelectsStudentWorkspace() throws Exception {
        PortalState state = state(
                new JSONArray().put("学生"),
                new JSONArray().put("education:grade:view"),
                new JSONArray().put("STUDENT"),
                new JSONArray());

        assertEquals(PortalState.Workspace.STUDENT, state.workspace);
    }

    @Test
    public void teacherProfileSelectsTeacherWorkspace() throws Exception {
        PortalState state = state(
                new JSONArray().put("教师"),
                new JSONArray().put("education:schedule:view"),
                new JSONArray().put("TEACHER"),
                new JSONArray());

        assertEquals(PortalState.Workspace.TEACHER, state.workspace);
    }

    @Test
    public void managementRoleAndCapabilityOverrideTeacherProfile() throws Exception {
        JSONArray applications = new JSONArray().put(new JSONObject()
                .put("code", "supervision-center")
                .put("name", "督办中心")
                .put("routePath", "/portal/education/supervision"));
        PortalState state = state(
                new JSONArray().put("校领导").put("教师"),
                new JSONArray().put("education:supervision:view"),
                new JSONArray().put("TEACHER"),
                applications);

        assertEquals(PortalState.Workspace.MANAGEMENT, state.workspace);
    }

    @Test
    public void employeeWithoutEducationProfileUsesStaffWorkspace() throws Exception {
        PortalState state = state(
                new JSONArray().put("教职工"),
                new JSONArray().put("portal:view"),
                new JSONArray(),
                new JSONArray());

        assertEquals(PortalState.Workspace.STAFF, state.workspace);
    }

    private static PortalState state(
            JSONArray roles,
            JSONArray permissions,
            JSONArray profileTypes,
            JSONArray applications) throws Exception {
        JSONObject dataContribution = new JSONObject()
                .put("available", true)
                .put("data", new JSONObject().put("profileTypes", profileTypes));
        JSONObject data = new JSONObject()
                .put("user", new JSONObject()
                        .put("displayName", "测试用户")
                        .put("roles", roles)
                        .put("permissions", permissions))
                .put("applications", applications)
                .put("contributions", new JSONObject().put("DATA", dataContribution));
        return PortalState.from(new JSONObject().put("data", data));
    }
}
