package com.chronos.commons.eumns;

public enum JiMengHttpCodeEnum {
	ECSuccess("10000", "请求成功"),

	ECReqInvalidArgs("50200", "参数错误"), ECReqMissingArgs("50201", "缺少参数"), ECParseArgs("50204", "参数类型错误/参数缺失"),

	ECImageSizeLimited("50205", "图像尺寸超过限制"), ECImageEmpty("50206", "请求参数中没有获取到图像"),
	ECImageDecodeError("50207", "图像解码错误"),

	ECVideoEmpty("50209", "请求参数中没有获取到视频"), ECVideoDecodeError("50210", "视频解码错误"),
	ECVideoSizeLimited("50211", "视频尺寸超过限制"), ECReqBodySizeLimited("50213", "请求Body过大，超出接口限制"),
	ECVideoTimeTooLong("50214", "输入视频时长过大"), ECRPCProcess("50215", "请求处理失败"),

	ECJPFaceDetect("60102", "未检测到人脸"), ECFSLeaderRiskError("60208", "输入图片包含敏感信息"),

	ECAuth("50400", "权限校验失败"),

	ECReqMethod("50402", "访问的接口不存在"), ECReqLimit("50429", "超过调用QPS限制"),

	ECInternal("50500", "服务器内部错误"), ECRPCInternal("50501", "服务器内部RPC错误");

	private String status;
	private String statusName;

	JiMengHttpCodeEnum(String status, String statusName) {
		this.status = status;
		this.statusName = statusName;
	}

	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getStatusName() {
		return this.statusName;
	}

	public void setStatusName(String statusName) {
		this.statusName = statusName;
	}
}
