package com.chronos.education.scheduling.model;

import java.util.List;

/** 调课事故批量重放命令；服务端会去重并限制单批数量。 */
public record CourseAdjustmentIncidentBatchCommand(List<String> ids) {
}
