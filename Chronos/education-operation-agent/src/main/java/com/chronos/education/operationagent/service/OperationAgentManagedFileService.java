package com.chronos.education.operationagent.service;

import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;

@Service
public class OperationAgentManagedFileService {
	private final ManagedFileService files;
	private final OperationAgentManagedFileAccessPolicy accessPolicy;

	public OperationAgentManagedFileService(
			ManagedFileService files,
			OperationAgentManagedFileAccessPolicy accessPolicy) {
		this.files = files;
		this.accessPolicy = accessPolicy;
	}

	/** Uploads a persistent roster owned by the current plan author. */
	public ManagedFileView uploadAdmissionRoster(MultipartFile file, Authentication authentication) {
		String filename = file == null ? null : file.getOriginalFilename();
		if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new IllegalArgumentException("新生名册必须是 xlsx 文件");
		}
		return files.upload(
				file,
				OperationAgentManagedFileAccessPolicy.ADMISSION_ROSTER,
				accessPolicy.newBusinessId(authentication.getName()),
				authentication);
	}
}
