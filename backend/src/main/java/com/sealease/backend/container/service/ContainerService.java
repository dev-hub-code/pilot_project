package com.sealease.backend.container.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.container.dto.ContainerDetail;
import com.sealease.backend.container.dto.ContainerDocumentResponse;
import com.sealease.backend.container.dto.ContainerRequest;
import com.sealease.backend.container.dto.ContainerSearchCriteria;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.entity.Container;
import com.sealease.backend.container.entity.ContainerDocument;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.container.repository.ContainerDocumentRepository;
import com.sealease.backend.container.repository.ContainerRepository;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.document.service.DocumentService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Container registry and its documents. */
@Service
public class ContainerService {

	private static final String ENTITY = "CONTAINER";

	private final ContainerRepository containers;
	private final ContainerDocumentRepository containerDocuments;
	private final DocumentService documents;
	private final ContainerUsage usage;
	private final AuditService audit;

	public ContainerService(ContainerRepository containers, ContainerDocumentRepository containerDocuments,
			DocumentService documents, ContainerUsage usage, AuditService audit) {
		this.containers = containers;
		this.containerDocuments = containerDocuments;
		this.documents = documents;
		this.usage = usage;
		this.audit = audit;
	}

	@Transactional
	public ContainerDetail create(UUID actorId, ContainerRequest request) {
		String number = ContainerNumbers.normalize(request.containerNumber());
		if (!ContainerNumbers.isValid(number)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Container number is not a valid ISO 6346 number (check the format and check digit)");
		}
		if (containers.existsByContainerNumber(number)) {
			throw new BusinessException(ErrorCode.CONFLICT, "Container " + number + " is already registered");
		}
		Container container = new Container(number, request.containerType(), actorId);
		describe(container, request);
		containers.saveAndFlush(container);
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_CREATED, ENTITY, container.getId())
			.withNewValue(Map.of("containerNumber", number, "type", request.containerType())));
		return detail(container.getId());
	}

	@Transactional
	public ContainerDetail update(UUID actorId, UUID containerId, ContainerRequest request) {
		Container container = lock(containerId);
		if (!container.getContainerNumber().equals(ContainerNumbers.normalize(request.containerNumber()))
				|| container.getContainerType() != request.containerType()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Container number and type cannot be changed once registered");
		}
		describe(container, request);
		containers.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_UPDATED, ENTITY, containerId));
		return detail(containerId);
	}

	@Transactional
	public ContainerDetail changeStatus(UUID actorId, UUID containerId, ContainerStatus status, String reason) {
		Container container = lock(containerId);
		ContainerStatus previous = container.getStatus();
		if (previous == ContainerStatus.RETIRED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A retired container cannot be reinstated");
		}
		if (status == ContainerStatus.RETIRED && usage.hasLiveOffering(containerId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The container backs a live investment offering and cannot be retired");
		}
		container.changeStatus(status, reason.strip());
		containers.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_STATUS_CHANGED, ENTITY, containerId)
			.withOldValue(Map.of("status", previous))
			.withNewValue(Map.of("status", status, "reason", reason.strip())));
		return detail(containerId);
	}

	@Transactional
	public ContainerDocumentResponse addDocument(UUID actorId, UUID containerId, DocumentPurpose purpose, String title,
			boolean visibleToInvestors, MultipartFile file) {
		if (!purpose.isContainerDocument()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, purpose + " is not a container document type");
		}
		if (purpose.isImage() && !isImage(file)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Container photos must be JPEG or PNG images");
		}
		Container container = lock(containerId);
		UUID documentId = documents.store(actorId, purpose, file);
		ContainerDocument document = containerDocuments.saveAndFlush(new ContainerDocument(container.getId(),
				documentId, purpose, title.strip(), visibleToInvestors, actorId));
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_DOCUMENT_ADDED, ENTITY, containerId)
			.withNewValue(Map.of("documentId", documentId.toString(), "purpose", purpose,
					"visibleToInvestors", visibleToInvestors)));
		return ContainerDocumentResponse.from(document);
	}

	@Transactional
	public ContainerDocumentResponse setDocumentVisibility(UUID actorId, UUID containerId, UUID documentId,
			boolean visible) {
		ContainerDocument document = containerDocuments.findByContainerIdAndDocumentId(containerId, documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
		document.setVisibleToInvestors(visible);
		containerDocuments.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.CONTAINER_DOCUMENT_VISIBILITY_CHANGED, ENTITY, containerId)
			.withNewValue(Map.of("documentId", documentId.toString(), "visibleToInvestors", visible)));
		return ContainerDocumentResponse.from(document);
	}

	@Transactional(readOnly = true)
	public Page<ContainerDetail> search(ContainerSearchCriteria criteria, Pageable pageable) {
		return containers.findAll(matching(criteria), pageable).map(c -> ContainerDetail.from(c, List.of()));
	}

	@Transactional(readOnly = true)
	public ContainerDetail detail(UUID containerId) {
		Container container = load(containerId);
		List<ContainerDocumentResponse> docs = containerDocuments.findByContainerIdOrderByCreatedAtAsc(containerId)
			.stream()
			.map(ContainerDocumentResponse::from)
			.toList();
		return ContainerDetail.from(container, docs);
	}

	/** Staff access to any document of the container. */
	@Transactional(readOnly = true)
	public DocumentContent document(UUID containerId, UUID documentId) {
		containerDocuments.findByContainerIdAndDocumentId(containerId, documentId)
			.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
		return documents.load(documentId);
	}

	// ------------------------------------------------------- for other modules (read only)

	@Transactional(readOnly = true)
	public ContainerSummary summary(UUID containerId) {
		return ContainerSummary.from(load(containerId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, ContainerSummary> summaries(Collection<UUID> containerIds) {
		Map<UUID, ContainerSummary> result = new LinkedHashMap<>();
		containers.findAllById(containerIds).forEach(c -> result.put(c.getId(), ContainerSummary.from(c)));
		return result;
	}

	@Transactional(readOnly = true)
	public List<UUID> idsOfType(ContainerType type) {
		return containers.findAll(matching(new ContainerSearchCriteria(null, null, type))).stream()
			.map(Container::getId)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ContainerDocumentResponse> investorDocuments(UUID containerId) {
		return containerDocuments.findByContainerIdAndVisibleToInvestorsTrueOrderByCreatedAtAsc(containerId).stream()
			.map(ContainerDocumentResponse::from)
			.toList();
	}

	/** First investor-visible photo of each container. */
	@Transactional(readOnly = true)
	public Map<UUID, UUID> coverPhotos(Collection<UUID> containerIds) {
		Map<UUID, UUID> covers = new LinkedHashMap<>();
		if (containerIds.isEmpty()) {
			return covers;
		}
		containerDocuments.findVisibleByContainersAndPurpose(containerIds, DocumentPurpose.CONTAINER_PHOTO)
			.forEach(d -> covers.putIfAbsent(d.getContainerId(), d.getDocumentId()));
		return covers;
	}

	@Transactional(readOnly = true)
	public boolean hasInvestorPhoto(UUID containerId) {
		return containerDocuments.existsByContainerIdAndPurposeAndVisibleToInvestorsTrue(containerId,
				DocumentPurpose.CONTAINER_PHOTO);
	}

	/** Investor access: only documents explicitly marked visible. */
	@Transactional(readOnly = true)
	public Optional<DocumentContent> investorDocument(UUID containerId, UUID documentId) {
		return containerDocuments.findByContainerIdAndDocumentId(containerId, documentId)
			.filter(ContainerDocument::isVisibleToInvestors)
			.map(d -> documents.load(documentId));
	}

	private void describe(Container container, ContainerRequest r) {
		if (r.tareKg() >= r.maxGrossKg()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tare weight must be less than maximum gross weight");
		}
		if ((r.acquisitionCost() == null) != (r.acquisitionCurrency() == null || r.acquisitionCurrency().isBlank())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Acquisition cost and currency must be given together");
		}
		container.describe(r.condition(), r.capacityCbm(), r.maxGrossKg(), r.tareKg(), r.manufactureYear().shortValue(),
				blankToNull(r.manufacturer()), r.currentLocation().strip(), r.locationCountry(), r.acquisitionCost(),
				blankToNull(r.acquisitionCurrency()), blankToNull(r.notes()));
	}

	private Container load(UUID id) {
		return containers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Container", id));
	}

	private Container lock(UUID id) {
		return containers.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Container", id));
	}

	private static boolean isImage(MultipartFile file) {
		try {
			String type = DocumentService.detectContentType(file.getBytes());
			return "image/jpeg".equals(type) || "image/png".equals(type);
		}
		catch (IOException ex) {
			return false;
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static Specification<Container> matching(ContainerSearchCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.q() != null && !criteria.q().isBlank()) {
				String like = "%" + criteria.q().strip().toLowerCase(Locale.ROOT)
					.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("containerNumber")), like, '\\'),
						cb.like(cb.lower(root.get("currentLocation")), like, '\\')));
			}
			if (criteria.status() != null) {
				predicates.add(cb.equal(root.get("status"), criteria.status()));
			}
			if (criteria.containerType() != null) {
				predicates.add(cb.equal(root.get("containerType"), criteria.containerType()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
