package com.chronos.knowledge.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.model.ResultData;
import com.chronos.knowledge.model.KnowledgeBase;
import com.chronos.knowledge.model.KnowledgeDocument;
import com.chronos.knowledge.service.KnowledgeService;
import com.chronos.knowledge.service.KnowledgeService.DocumentCommand;
import com.chronos.knowledge.service.KnowledgeService.AssistantAnswer;
import com.chronos.knowledge.service.KnowledgeService.SearchResult;

@RestController
@PreAuthorize("@iamAuthorization.any(authentication,'education:ai:knowledge:view','education:ai:knowledge:manage')")
public class KnowledgeController {
	private final KnowledgeService service;

	public KnowledgeController(KnowledgeService service) {
		this.service = service;
	}

	@GetMapping("/admin/knowledge-bases")
	public ResultData<?> knowledgeBases(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null
				? ok(service.knowledgeBases())
				: ok(service.knowledgeBases(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/knowledge-bases")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:knowledge:manage')")
	public ResultData<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase command) {
		return ok(service.saveKnowledgeBase(null, command));
	}

	@PutMapping("/admin/knowledge-bases/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:knowledge:manage')")
	public ResultData<KnowledgeBase> updateKnowledgeBase(
			@PathVariable String id,
			@RequestBody KnowledgeBase command) {
		return ok(service.saveKnowledgeBase(id, command));
	}

	@DeleteMapping("/admin/knowledge-bases/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:knowledge:manage')")
	public ResultData<Void> deleteKnowledgeBase(@PathVariable String id) {
		service.deleteKnowledgeBase(id);
		return ok(null);
	}

	@GetMapping("/admin/knowledge-documents")
	public ResultData<?> documents(
			@RequestParam String knowledgeBaseId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null
				? ok(service.documents(knowledgeBaseId))
				: ok(service.documents(knowledgeBaseId, page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/knowledge-documents/text")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:knowledge:manage')")
	public ResultData<KnowledgeDocument> createTextDocument(@RequestBody DocumentCommand command) {
		return ok(service.createTextDocument(command));
	}

	@PostMapping("/admin/knowledge-documents/import")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:knowledge:import')")
	public ResultData<KnowledgeDocument> importDocument(
			@RequestParam String knowledgeBaseId,
			@RequestParam(required = false) String title,
			@RequestPart("file") MultipartFile file) throws IOException {
		return ok(service.importDocument(knowledgeBaseId, title, file));
	}

	@DeleteMapping("/admin/knowledge-documents/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:knowledge:delete')")
	public ResultData<Void> deleteDocument(@PathVariable String id) {
		service.deleteDocument(id);
		return ok(null);
	}

	@GetMapping("/admin/knowledge-search")
	public ResultData<List<SearchResult>> search(
			@RequestParam String knowledgeBaseId,
			@RequestParam String keyword,
			@RequestParam(required = false) Integer limit) {
		return ok(service.search(knowledgeBaseId, keyword, limit));
	}

	@PostMapping("/education/ai/assistant/ask")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:assistant:use')")
	public ResultData<AssistantAnswer> ask(
			@RequestBody AssistantCommand command,
			Authentication authentication) {
		return ok(service.ask(
				command.knowledgeBaseId(),
				command.question(),
				authentication));
	}

	public record AssistantCommand(
			String knowledgeBaseId,
			String question) {
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
