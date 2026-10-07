package com.sealease.backend.permission.service;

import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.permission.repository.PermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Fails startup if code references a permission that the database does not define, which would
 * otherwise make the guarded endpoints silently unreachable for every role.
 */
@Component
public class PermissionCatalogVerifier implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PermissionCatalogVerifier.class);

	private final PermissionRepository permissions;

	public PermissionCatalogVerifier(PermissionRepository permissions) {
		this.permissions = permissions;
	}

	@Override
	public void run(ApplicationArguments args) {
		Set<String> inDatabase = permissions.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
		Set<String> inCode = Arrays.stream(PermissionCode.values()).map(Enum::name).collect(Collectors.toSet());

		Set<String> missing = new TreeSet<>(inCode);
		missing.removeAll(inDatabase);
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Permissions missing from database (add a migration): " + missing);
		}
		Set<String> unknown = new TreeSet<>(inDatabase);
		unknown.removeAll(inCode);
		if (!unknown.isEmpty()) {
			log.warn("Database defines permissions not referenced in code: {}", unknown);
		}
	}

}
