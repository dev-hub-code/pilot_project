package com.sealease.backend.kyc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** At startup, fingerprints the Aadhaar and PAN of KYC submissions made before duplicates were checked. */
@Component
public class KycFingerprintBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(KycFingerprintBackfill.class);

	private final KycService kyc;

	public KycFingerprintBackfill(KycService kyc) {
		this.kyc = kyc;
	}

	@Override
	public void run(ApplicationArguments args) {
		int done = kyc.fingerprintEarlierSubmissions();
		if (done > 0) {
			log.info("Fingerprinted the Aadhaar and PAN of {} earlier KYC submission(s)", done);
		}
	}

}
