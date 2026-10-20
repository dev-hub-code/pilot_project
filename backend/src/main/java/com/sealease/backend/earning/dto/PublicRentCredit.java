package com.sealease.backend.earning.dto;

/** How often rent is credited, for wording on the website and the portal, e.g. "every hour". */
public record PublicRentCredit(long intervalMinutes, String label) {
}
