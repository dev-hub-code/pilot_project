package com.sealease.backend.permission.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.permission.dto.PermissionResponse;
import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.permission.repository.PermissionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class PermissionService {

	private final PermissionRepository permissions;

	public PermissionService(PermissionRepository permissions) {
		this.permissions = permissions;
	}

	@Transactional(readOnly = true)
	public List<PermissionResponse> listAll() {
		return permissions.findAll(Sort.by("category", "code")).stream().map(PermissionResponse::from).toList();
	}

	/**
	 * Resolves permission codes to entities, rejecting unknown codes.
	 */
	@Transactional(readOnly = true)
	public Set<Permission> resolve(Collection<String> codes) {
		Set<String> requested = new TreeSet<>(codes);
		List<Permission> found = permissions.findByCodeIn(requested);
		if (found.size() != requested.size()) {
			Set<String> known = found.stream().map(Permission::getCode).collect(Collectors.toSet());
			requested.removeAll(known);
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown permissions: " + requested);
		}
		return Set.copyOf(found);
	}

}
