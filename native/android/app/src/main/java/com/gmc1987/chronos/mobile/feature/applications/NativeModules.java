package com.gmc1987.chronos.mobile.feature.applications;

import java.util.ArrayList;
import java.util.List;

/** Native module definitions corresponding to the iOS application groups. */
final class NativeModules {
    private NativeModules() {}

    static List<NativeModule> collaboration() {
        List<NativeModule> result = new ArrayList<>();
        result.add(new NativeModule("请假", "发起请假并查看审批状态", NativeModule.LEAVES, ""));
        result.add(new NativeModule("用车与用印", "发起申请并查看执行状态",
                NativeModule.OFFICE_REQUESTS, ""));
        result.add(new NativeModule("出差", "发起出差并查看行程、预算和审批",
                NativeModule.BUSINESS_TRIPS, ""));
        result.add(new NativeModule("公文", "收发、签发和归档",
                "/portal/collaboration/documents/my"));
        result.add(new NativeModule("共享文件", "统一文件共享",
                "/portal/collaboration/files/shares"));
        result.add(new NativeModule("通讯录", "教职工联系方式",
                "/portal/collaboration/contacts?keyword="));
        return result;
    }

    static List<NativeModule> teaching() {
        String[][] values = {{"plans", "教学计划"}, {"lesson-plans", "教案"},
                {"preparations", "集体备课"}, {"coursewares", "课件"},
                {"materials", "教学材料"}, {"questions", "题库"},
                {"knowledge-points", "知识点"}, {"research", "教研"},
                {"mistakes", "错题沉淀"}};
        List<NativeModule> result = new ArrayList<>();
        for (String[] value : values) {
            result.add(new NativeModule(value[1], "查看和管理" + value[1],
                    "/education/teaching-center/api/" + value[0] + "?page=0&size=50"));
        }
        return result;
    }
}
