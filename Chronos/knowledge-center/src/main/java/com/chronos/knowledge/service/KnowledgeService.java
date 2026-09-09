package com.chronos.knowledge.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.utils.DocToMarkdownUtil;
import com.chronos.knowledge.dao.KnowledgeBaseRepository;
import com.chronos.knowledge.dao.KnowledgeChunkRepository;
import com.chronos.knowledge.dao.KnowledgeDocumentRepository;
import com.chronos.knowledge.model.KnowledgeBase;
import com.chronos.knowledge.model.KnowledgeChunk;
import com.chronos.knowledge.model.KnowledgeDocument;
import com.chronos.service.factory.LLMServiceStrategy;
import com.chronos.service.iService.IAuditLogService;

/** 知识库最小生产闭环：管理、导入、稳定分段、检索和可追溯引用。 */
@Service
public class KnowledgeService {
	private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
	private static final int MAX_CHUNK_LENGTH = 1000;
	private static final int DEFAULT_SEARCH_LIMIT = 20;
	private static final int MAX_SEARCH_LIMIT = 100;

	private final KnowledgeBaseRepository knowledgeBases;
	private final KnowledgeDocumentRepository documents;
	private final KnowledgeChunkRepository chunks;
	private final LLMServiceStrategy llmService;
	private final IAuditLogService auditLogService;

	public KnowledgeService(
			KnowledgeBaseRepository knowledgeBases,
			KnowledgeDocumentRepository documents,
			KnowledgeChunkRepository chunks,
			@Qualifier("deepseekService") LLMServiceStrategy llmService,
			IAuditLogService auditLogService) {
		this.knowledgeBases = knowledgeBases;
		this.documents = documents;
		this.chunks = chunks;
		this.llmService = llmService;
		this.auditLogService = auditLogService;
	}

	public List<KnowledgeBase> knowledgeBases() {
		return knowledgeBases.findAllByOrderByCreateTimeDesc();
	}

	@Transactional
	public KnowledgeBase saveKnowledgeBase(String id, KnowledgeBase command) {
		requireText(command.getBaseCode(), "知识库编码不能为空");
		requireText(command.getBaseName(), "知识库名称不能为空");
		KnowledgeBase value = id == null
				? new KnowledgeBase()
				: knowledgeBases.findById(id)
						.orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
		if (id == null && knowledgeBases.existsByBaseCode(command.getBaseCode().trim())) {
			throw new IllegalArgumentException("知识库编码已存在");
		}
		value.setBaseCode(command.getBaseCode().trim());
		value.setBaseName(command.getBaseName().trim());
		value.setDescription(trimToNull(command.getDescription()));
		value.setOrganizationId(trimToNull(command.getOrganizationId()));
		value.setEnabled(command.getEnabled() == null || command.getEnabled());
		return knowledgeBases.save(value);
	}

	@Transactional
	public void deleteKnowledgeBase(String id) {
		if (documents.countByKnowledgeBaseId(id) > 0) {
			throw new IllegalArgumentException("知识库下存在文档，不能删除");
		}
		knowledgeBases.deleteById(id);
	}

	public List<KnowledgeDocument> documents(String knowledgeBaseId) {
		knowledgeBases.findById(knowledgeBaseId)
				.orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
		return documents.findByKnowledgeBaseIdOrderByCreateTimeDesc(knowledgeBaseId);
	}

	@Transactional
	public KnowledgeDocument createTextDocument(DocumentCommand command) {
		requireText(command.knowledgeBaseId(), "知识库不能为空");
		requireText(command.title(), "文档标题不能为空");
		requireText(command.content(), "文档内容不能为空");
		return persistDocument(
				command.knowledgeBaseId(),
				command.title(),
				"TEXT",
				null,
				command.content());
	}

	@Transactional
	public KnowledgeDocument importDocument(
			String knowledgeBaseId,
			String title,
			MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择要导入的文件");
		}
		if (file.getSize() > MAX_DOCUMENT_BYTES) {
			throw new IllegalArgumentException("文档不能超过 10MB");
		}
		String filename = file.getOriginalFilename();
		String content = readContent(file, filename);
		String resolvedTitle = trimToNull(title);
		if (resolvedTitle == null) {
			resolvedTitle = filename;
		}
		return persistDocument(
				knowledgeBaseId,
				resolvedTitle,
				"UPLOAD",
				filename,
				content);
	}

	@Transactional
	public void deleteDocument(String id) {
		if (!documents.existsById(id)) {
			throw new IllegalArgumentException("知识文档不存在");
		}
		chunks.deleteByDocumentId(id);
		documents.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<SearchResult> search(String knowledgeBaseId, String keyword, Integer limit) {
		requireText(keyword, "检索关键词不能为空");
		knowledgeBases.findById(knowledgeBaseId)
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
				.orElseThrow(() -> new IllegalArgumentException("知识库不存在或已停用"));
		int safeLimit = limit == null
				? DEFAULT_SEARCH_LIMIT
				: Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
		List<KnowledgeChunk> matches = chunks.search(
				knowledgeBaseId,
				keyword.trim(),
				PageRequest.of(0, safeLimit));
		Map<String, KnowledgeDocument> documentMap = new HashMap<>();
		for (KnowledgeDocument document : documents.findByKnowledgeBaseIdOrderByCreateTimeDesc(knowledgeBaseId)) {
			documentMap.put(document.getId(), document);
		}
		return matches.stream().map(chunk -> {
			KnowledgeDocument document = documentMap.get(chunk.getDocumentId());
			return new SearchResult(
					chunk.getDocumentId(),
					document == null ? "未知文档" : document.getTitle(),
					document == null ? null : document.getOriginalFilename(),
					chunk.getChunkIndex(),
					chunk.getContent());
		}).toList();
	}

	/**
	 * 教育助手只允许依据命中的校内知识回答，并把引用作为结构化字段返回。
	 * 模型无法调用时直接报错，禁止用模板文本伪装成模型结论。
	 */
	public AssistantAnswer ask(
			String knowledgeBaseId,
			String question,
			Authentication authentication) {
		requireText(question, "问题不能为空");
		List<SearchResult> references = relevantReferences(knowledgeBaseId, question, 5);
		if (references.isEmpty()) {
			return new AssistantAnswer(
					"当前知识库没有找到足够依据，请联系知识库管理员补充资料。",
					List.of(),
					false);
		}
		StringBuilder prompt = new StringBuilder();
		prompt.append("你是学校教务助手。只能依据下列资料回答，不得编造制度或数据。")
				.append("回答应简洁，并在相关结论后标注[资料1]格式的来源编号。\n\n")
				.append("用户问题：").append(question.trim()).append("\n\n");
		for (int index = 0; index < references.size(); index++) {
			SearchResult reference = references.get(index);
			prompt.append("[资料").append(index + 1).append("] ")
					.append(reference.documentTitle())
					.append(" 第").append(reference.chunkIndex()).append("段\n")
					.append(reference.content()).append("\n\n");
		}
		String answer = llmService.chat(prompt.toString());
		String username = authentication == null ? "UNKNOWN" : authentication.getName();
		auditLogService.log(
				username,
				"EDUCATION_AI_ASSISTANT_ASK",
				"knowledgeBaseId=" + knowledgeBaseId + ", referenceCount=" + references.size());
		return new AssistantAnswer(answer, references, true);
	}

	private List<SearchResult> relevantReferences(
			String knowledgeBaseId,
			String question,
			int limit) {
		knowledgeBases.findById(knowledgeBaseId)
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
				.orElseThrow(() -> new IllegalArgumentException("知识库不存在或已停用"));
		Set<String> terms = retrievalTerms(question);
		Map<String, KnowledgeChunk> matches = new LinkedHashMap<>();
		for (String term : terms) {
			for (KnowledgeChunk chunk : chunks.search(
					knowledgeBaseId,
					term,
					PageRequest.of(0, limit))) {
				matches.putIfAbsent(chunk.getId(), chunk);
				if (matches.size() >= limit) {
					break;
				}
			}
			if (matches.size() >= limit) {
				break;
			}
		}
		Map<String, KnowledgeDocument> documentMap = new HashMap<>();
		for (KnowledgeDocument document : documents.findByKnowledgeBaseIdOrderByCreateTimeDesc(knowledgeBaseId)) {
			documentMap.put(document.getId(), document);
		}
		return matches.values().stream().map(chunk -> {
			KnowledgeDocument document = documentMap.get(chunk.getDocumentId());
			return new SearchResult(
					chunk.getDocumentId(),
					document == null ? "未知文档" : document.getTitle(),
					document == null ? null : document.getOriginalFilename(),
					chunk.getChunkIndex(),
					chunk.getContent());
		}).toList();
	}

	/** 中文问句没有天然空格，使用原问句、分词和连续双字词逐级召回。 */
	private Set<String> retrievalTerms(String question) {
		String normalized = question.trim().replaceAll("[\\p{P}\\p{S}\\s]+", " ");
		Set<String> terms = new LinkedHashSet<>();
		terms.add(normalized);
		for (String word : normalized.split(" ")) {
			if (word.length() >= 2) {
				terms.add(word);
			}
			for (int index = 0; index + 2 <= word.length(); index++) {
				terms.add(word.substring(index, index + 2));
			}
		}
		return terms;
	}

	private KnowledgeDocument persistDocument(
			String knowledgeBaseId,
			String title,
			String sourceType,
			String originalFilename,
			String content) {
		knowledgeBases.findById(knowledgeBaseId)
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
				.orElseThrow(() -> new IllegalArgumentException("知识库不存在或已停用"));
		requireText(title, "文档标题不能为空");
		requireText(content, "文档没有可入库的文本内容");
		List<String> segments = split(content);
		KnowledgeDocument document = new KnowledgeDocument();
		document.setKnowledgeBaseId(knowledgeBaseId);
		document.setTitle(title.trim());
		document.setSourceType(sourceType);
		document.setOriginalFilename(trimToNull(originalFilename));
		document.setContent(content.trim());
		document.setChunkCount(segments.size());
		document.setStatus("READY");
		document = documents.save(document);
		for (int index = 0; index < segments.size(); index++) {
			KnowledgeChunk chunk = new KnowledgeChunk();
			chunk.setDocumentId(document.getId());
			chunk.setChunkIndex(index + 1);
			chunk.setContent(segments.get(index));
			chunks.save(chunk);
		}
		return document;
	}

	private String readContent(MultipartFile file, String filename) throws IOException {
		String lower = filename == null ? "" : filename.toLowerCase();
		if (lower.endsWith(".txt") || lower.endsWith(".md")) {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		}
		if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
			return DocToMarkdownUtil.convert(file.getInputStream(), filename);
		}
		throw new IllegalArgumentException("首版仅支持 TXT、Markdown、DOC 和 DOCX 文档");
	}

	private List<String> split(String source) {
		String normalized = source.replace("\r\n", "\n").replace('\r', '\n').trim();
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String paragraph : normalized.split("\\n\\s*\\n|\\n")) {
			String value = paragraph.trim();
			if (value.isEmpty()) {
				continue;
			}
			if (current.length() > 0 && current.length() + value.length() + 1 > MAX_CHUNK_LENGTH) {
				result.add(current.toString());
				current.setLength(0);
			}
			while (value.length() > MAX_CHUNK_LENGTH) {
				if (current.length() > 0) {
					result.add(current.toString());
					current.setLength(0);
				}
				result.add(value.substring(0, MAX_CHUNK_LENGTH));
				value = value.substring(MAX_CHUNK_LENGTH);
			}
			if (!value.isEmpty()) {
				if (current.length() > 0) {
					current.append('\n');
				}
				current.append(value);
			}
		}
		if (current.length() > 0) {
			result.add(current.toString());
		}
		return result;
	}

	private static void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record DocumentCommand(
			String knowledgeBaseId,
			String title,
			String content) {
	}

	public record SearchResult(
			String documentId,
			String documentTitle,
			String originalFilename,
			Integer chunkIndex,
			String content) {
	}

	public record AssistantAnswer(
			String answer,
			List<SearchResult> references,
			boolean modelGenerated) {
	}
}
