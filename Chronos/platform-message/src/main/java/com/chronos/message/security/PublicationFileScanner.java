package com.chronos.message.security;

/** 可替换为 ClamAV 等企业病毒扫描实现；发现风险时直接抛出异常阻断上传。 */
public interface PublicationFileScanner {
	void scan(byte[] content, String filename, String contentType);
}
