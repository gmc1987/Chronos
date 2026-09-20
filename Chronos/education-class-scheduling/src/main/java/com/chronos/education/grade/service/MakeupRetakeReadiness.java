package com.chronos.education.grade.service;

import java.util.List;

/**
 * 补考/重修的能力闸门。
 *
 * <p>在考试中心提供可消费的确认事件、学生成绩结果模型和课程开设关联前，
 * 成绩中心必须保持不可用，不能通过人工请求创建没有来源的补考或重修成绩。</p>
 */
public record MakeupRetakeReadiness(
		boolean available,
		String code,
		String message,
		List<String> missingCapabilities) {

	public static MakeupRetakeReadiness unavailable() {
		return new MakeupRetakeReadiness(
				false,
				"MAKEUP_RETAKE_UNAVAILABLE",
				"补考/重修暂不可用：考试确认结果尚未接入成绩中心，且缺少补考/重修结果及课程开设关联模型。",
				List.of(
						"ExamScoresConfirmedV1 consumer with idempotent source binding",
						"makeup/retake result model linked to published source grade",
						"retake course offering and data-scope authorization"));
	}

	public void requireAvailable() {
		if (!available) {
			throw new IllegalStateException(message);
		}
	}
}
