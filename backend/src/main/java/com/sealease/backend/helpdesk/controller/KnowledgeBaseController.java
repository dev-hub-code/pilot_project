package com.sealease.backend.helpdesk.controller;

import com.sealease.backend.helpdesk.knowledgebase.KnowledgeBaseService;
import com.sealease.backend.helpdesk.knowledgebase.KnowledgeBaseService.Audience;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Knowledge base endpoints for the three audiences: the public, signed-in customers, and staff (read and edit). */
public final class KnowledgeBaseController {

	private KnowledgeBaseController() {
	}

	public record FeedbackRequest(@NotNull Boolean helpful, @Size(max = 1000) String comment) {
	}

	/** Anyone, signed in or not: published PUBLIC articles only. */
	@RestController
	@Validated
	@RequestMapping("/api/v1/open/knowledge-base")
	public static class Public {

		private final KnowledgeBaseService kb;

		public Public(KnowledgeBaseService kb) {
			this.kb = kb;
		}

		@GetMapping("/categories")
		public List<KnowledgeBaseService.Category> categories() {
			return kb.categories(Audience.PUBLIC);
		}

		@GetMapping("/articles")
		public KnowledgeBaseService.Page<KnowledgeBaseService.Summary> search(@RequestParam(required = false) @Size(max = 200) String q,
				@RequestParam(required = false) String category, @RequestParam(defaultValue = "recent") String sort,
				@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
			return kb.search(Audience.PUBLIC, q, category, sort, page, size);
		}

		@GetMapping("/articles/{slug}")
		public KnowledgeBaseService.Article read(@PathVariable String slug) {
			return kb.read(Audience.PUBLIC, slug);
		}

	}

	/** Signed-in customers: PUBLIC and CUSTOMER articles; they can say whether one helped. */
	@RestController
	@Validated
	@RequestMapping("/api/v1/support/knowledge-base")
	@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
	public static class Customer {

		private final KnowledgeBaseService kb;

		public Customer(KnowledgeBaseService kb) {
			this.kb = kb;
		}

		@GetMapping("/categories")
		public List<KnowledgeBaseService.Category> categories() {
			return kb.categories(Audience.CUSTOMER);
		}

		@GetMapping("/articles")
		public KnowledgeBaseService.Page<KnowledgeBaseService.Summary> search(@RequestParam(required = false) @Size(max = 200) String q,
				@RequestParam(required = false) String category, @RequestParam(defaultValue = "recent") String sort,
				@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
			return kb.search(Audience.CUSTOMER, q, category, sort, page, size);
		}

		@GetMapping("/articles/{slug}")
		public KnowledgeBaseService.Article read(@PathVariable String slug) {
			return kb.read(Audience.CUSTOMER, slug);
		}

		@PostMapping("/articles/{slug}/feedback")
		@ResponseStatus(HttpStatus.NO_CONTENT)
		public void feedback(AuthenticatedUser reader, @PathVariable String slug, @Valid @RequestBody FeedbackRequest request) {
			kb.feedback(Audience.CUSTOMER, reader.userId(), slug, request.helpful(), request.comment());
		}

	}

	/** Staff: read every published article (INTERNAL included); editors manage them. */
	@RestController
	@Validated
	@RequestMapping("/api/v1/admin/knowledge-base")
	@PreAuthorize("hasAnyAuthority('KNOWLEDGE_BASE_MANAGE', 'SYSTEM_SETTINGS_MANAGE')")
	public static class Admin {

		private static final String READ = "hasAnyAuthority('KNOWLEDGE_BASE_MANAGE', 'SYSTEM_SETTINGS_MANAGE', 'SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')";

		private final KnowledgeBaseService kb;

		public Admin(KnowledgeBaseService kb) {
			this.kb = kb;
		}

		@GetMapping("/categories")
		@PreAuthorize(READ)
		public List<KnowledgeBaseService.Category> categories() {
			return kb.categories(Audience.STAFF);
		}

		/** Published articles for staff (e.g. to answer a ticket). */
		@GetMapping("/search")
		@PreAuthorize(READ)
		public KnowledgeBaseService.Page<KnowledgeBaseService.Summary> search(@RequestParam(required = false) @Size(max = 200) String q,
				@RequestParam(required = false) String category, @RequestParam(defaultValue = "popular") String sort,
				@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
			return kb.search(Audience.STAFF, q, category, sort, page, size);
		}

		@GetMapping("/read/{slug}")
		@PreAuthorize(READ)
		public KnowledgeBaseService.Article read(@PathVariable String slug) {
			return kb.read(Audience.STAFF, slug);
		}

		@GetMapping("/articles")
		public KnowledgeBaseService.Page<KnowledgeBaseService.Summary> manage(@RequestParam(required = false) @Size(max = 200) String q,
				@RequestParam(required = false) String category, @RequestParam(required = false) String status,
				@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
			return kb.manage(q, category, status, page, size);
		}

		@GetMapping("/articles/{id}")
		public KnowledgeBaseService.Article article(@PathVariable UUID id) {
			return kb.edit(id);
		}

		@PostMapping("/articles")
		@ResponseStatus(HttpStatus.CREATED)
		public KnowledgeBaseService.Article create(AuthenticatedUser editor, @Valid @RequestBody KnowledgeBaseService.ArticleRequest request) {
			return kb.create(editor.userId(), request);
		}

		/** @param expectedVersion the article version the edit started from (0 to skip the check) */
		@PutMapping("/articles/{id}")
		public KnowledgeBaseService.Article update(AuthenticatedUser editor, @PathVariable UUID id,
				@RequestParam(defaultValue = "0") long expectedVersion, @Valid @RequestBody KnowledgeBaseService.ArticleRequest request) {
			return kb.update(editor.userId(), id, expectedVersion, request);
		}

		@PostMapping("/articles/{id}/publish")
		public KnowledgeBaseService.Article publish(AuthenticatedUser editor, @PathVariable UUID id) {
			return kb.publish(editor.userId(), id);
		}

		@PostMapping("/articles/{id}/unpublish")
		public KnowledgeBaseService.Article unpublish(AuthenticatedUser editor, @PathVariable UUID id) {
			return kb.unpublish(editor.userId(), id);
		}

		@PostMapping("/articles/{id}/archive")
		public KnowledgeBaseService.Article archive(AuthenticatedUser editor, @PathVariable UUID id) {
			return kb.archive(editor.userId(), id);
		}

		@GetMapping("/articles/{id}/versions")
		public List<KnowledgeBaseService.Version> versions(@PathVariable UUID id) {
			return kb.versions(id);
		}

		@GetMapping("/articles/{id}/feedback")
		public KnowledgeBaseService.FeedbackStats feedback(@PathVariable UUID id) {
			return kb.feedbackFor(id);
		}

	}

}
