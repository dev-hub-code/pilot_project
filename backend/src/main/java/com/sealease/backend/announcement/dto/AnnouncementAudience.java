package com.sealease.backend.announcement.dto;

/**
 * Who an announcement goes to. Only active accounts receive it: suspended and disabled ones never do. Investors are
 * online (registered themselves) or network (made by an administrator, listed in {@code network_investors}), as in
 * investor management and the referral schemes.
 */
public enum AnnouncementAudience {

	/** Every active account: investors and staff. */
	EVERYONE,
	/** Every active investor (accounts with the investor portal). */
	INVESTORS,
	/** Active investors who registered themselves. */
	ONLINE_INVESTORS,
	/** Active investors made by an administrator. */
	NETWORK_INVESTORS,
	/** Active staff accounts: everyone without the investor portal. */
	STAFF

}
