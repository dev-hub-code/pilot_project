package com.sealease.backend.auth.bootstrap;

import com.sealease.backend.auth.config.BootstrapProperties;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first super-administrator from environment configuration.
 *
 * <p>Runs only while nobody holds the super-admin role. It never promotes an existing account:
 * otherwise anyone who self-registered with the configured email first would become super admin.
 */
@Component
@EnableConfigurationProperties(BootstrapProperties.class)
public class SuperAdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

	private final BootstrapProperties properties;
	private final UserAccountService accounts;
	private final UserRoleService userRoles;
	private final TransactionTemplate tx;

	public SuperAdminBootstrap(BootstrapProperties properties, UserAccountService accounts, UserRoleService userRoles,
			PlatformTransactionManager transactionManager) {
		this.properties = properties;
		this.accounts = accounts;
		this.userRoles = userRoles;
		this.tx = new TransactionTemplate(transactionManager);
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured() || userRoles.anyUserHasRole(properties.role())) {
			return;
		}
		if (accounts.findByEmail(properties.email()).isPresent()) {
			log.error("Super-admin bootstrap skipped: an account with the configured email already exists and "
					+ "will not be promoted automatically. Configure a different bootstrap email.");
			return;
		}
		tx.executeWithoutResult(status -> {
			UserAccount admin = accounts.createSystemAccount(properties.email(), properties.password(),
					properties.firstName(), properties.lastName());
			userRoles.grantSystemRole(admin.id(), properties.role());
		});
		log.warn("Bootstrap super-admin account created. Remove the bootstrap password from the environment now.");
	}

}
