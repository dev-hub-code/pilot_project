package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.document.service.DocumentService;
import com.sealease.backend.investment.dto.PlanPhotoResponse;
import com.sealease.backend.investment.entity.InvestmentProduct;
import com.sealease.backend.investment.entity.PlanPhoto;
import com.sealease.backend.investment.entity.ProductStatus;
import com.sealease.backend.investment.repository.InvestmentProductRepository;
import com.sealease.backend.investment.repository.PlanPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Photos of a plan, shown in the marketplace (first one as the cover). Admins with INVESTMENT_UPDATE add and remove
 * them; the images are type-checked and stored encrypted in the document store.
 */
@Service
public class PlanPhotoService {

	public static final int MAX_PHOTOS = 10;
	private static final String ENTITY = "INVESTMENT_PRODUCT";

	private final PlanPhotoRepository photos;
	private final InvestmentProductRepository products;
	private final DocumentService documents;
	private final AuditService audit;
	private final Clock clock;

	public PlanPhotoService(PlanPhotoRepository photos, InvestmentProductRepository products, DocumentService documents,
			AuditService audit, Clock clock) {
		this.photos = photos;
		this.products = products;
		this.documents = documents;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PlanPhotoResponse> list(UUID productId) {
		return photos.findByProductIdOrderByCreatedAtAscIdAsc(productId).stream().map(PlanPhotoResponse::from).toList();
	}

	@Transactional
	public PlanPhotoResponse add(UUID actorId, UUID productId, MultipartFile file) {
		InvestmentProduct product = products.findById(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Container listing", productId));
		if (product.getStatus() == ProductStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A cancelled listing takes no photos");
		}
		if (photos.countByProductId(productId) >= MAX_PHOTOS) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"A listing has at most " + MAX_PHOTOS + " photos; remove one first");
		}
		byte[] bytes = read(file);
		String type = DocumentService.detectContentType(bytes);
		if (!"image/jpeg".equals(type) && !"image/png".equals(type)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Photos must be JPEG or PNG images");
		}
		UUID documentId = documents.store(actorId, DocumentPurpose.PLAN_PHOTO, bytes);
		PlanPhoto photo = photos.saveAndFlush(new PlanPhoto(productId, documentId, actorId, clock.instant()));
		audit.record(AuditRecord.of(actorId, AuditAction.PLAN_PHOTO_ADDED, ENTITY, productId)
			.withNewValue(Map.of("photoId", photo.getId().toString(), "documentId", documentId.toString())));
		return PlanPhotoResponse.from(photo);
	}

	@Transactional
	public void remove(UUID actorId, UUID productId, UUID photoId) {
		PlanPhoto photo = photos.findById(photoId).filter(p -> p.getProductId().equals(productId))
			.orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
		photos.delete(photo);
		audit.record(AuditRecord.of(actorId, AuditAction.PLAN_PHOTO_REMOVED, ENTITY, productId)
			.withOldValue(Map.of("photoId", photoId.toString(), "documentId", photo.getDocumentId().toString())));
	}

	/** One of the plan's photos, by its document id. */
	@Transactional(readOnly = true)
	public Optional<DocumentContent> photo(UUID productId, UUID documentId) {
		return photos.findByProductIdAndDocumentId(productId, documentId).map(p -> documents.load(documentId));
	}

	/** The plan's photos (document ids), oldest first, at most {@code limit}. */
	@Transactional(readOnly = true)
	public List<UUID> documentIds(UUID productId, int limit) {
		return photos.findByProductIdOrderByCreatedAtAscIdAsc(productId).stream()
			.limit(limit).map(PlanPhoto::getDocumentId).toList();
	}

	/** The first photo of each plan that has one. */
	@Transactional(readOnly = true)
	public Map<UUID, UUID> covers(Collection<UUID> productIds) {
		Map<UUID, UUID> covers = new LinkedHashMap<>();
		if (!productIds.isEmpty()) {
			photos.findByProductIdInOrderByCreatedAtAscIdAsc(productIds)
				.forEach(p -> covers.putIfAbsent(p.getProductId(), p.getDocumentId()));
		}
		return covers;
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read an uploaded file", ex);
		}
	}

}
