package com.chronos.message.service.iService;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.message.model.PublicationCommand;
import com.chronos.message.model.PublicationView;
import com.chronos.message.model.PublicationStatistics;
import com.chronos.message.model.PublicationVersion;

public interface IPublicationService {
	Page<PublicationView> adminList(
			String type,
			String status,
			String keyword,
			String username,
			Pageable pageable);

	PublicationView adminDetail(String id, String username);

	PublicationView create(PublicationCommand command, String username);

	PublicationView update(String id, PublicationCommand command, String username);

	PublicationView publish(String id, String username);

	PublicationView submit(String id, String username);

	PublicationView approve(String id, String comment, String username);

	PublicationView reject(String id, String comment, String username);

	PublicationView withdraw(String id, String username);

	void delete(String id, String username);

	Page<PublicationView> visible(
			String username,
			String type,
			String keyword,
			boolean unreadOnly,
			Pageable pageable);

	PublicationView portalDetail(String id, String username);

	void markRead(String id, String username);

	PublicationView attach(
			String publicationId,
			MultipartFile file,
			boolean primaryContent,
			String username);

	void deleteAttachment(String attachmentId, String username);

	PublicationStatistics statistics(String id, String username);

	List<PublicationVersion> versions(String id, String username);

	PublicationView restoreVersion(String id, Integer versionNo, String username);

	PublicationView archive(String id, boolean archived, String username);

	long remindUnread(String id, String username);

	DownloadFile download(String attachmentId, String username, boolean administrator);

	void advanceLifecycle();

	record DownloadFile(String filename, String contentType, byte[] content) {
	}
}
