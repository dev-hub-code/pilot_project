package com.sealease.backend.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param maxFileSize largest accepted upload; must not exceed spring.servlet.multipart.max-file-size
 * @param s3 where new documents are stored; without a bucket, in the database
 */
@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(@DefaultValue("5MB") DataSize maxFileSize, @DefaultValue S3 s3) {

	/**
	 * @param bucket the S3 bucket, or blank to keep documents in the database
	 * @param region the bucket's region
	 */
	public record S3(String bucket, @DefaultValue("ap-southeast-2") String region) {

		public boolean enabled() {
			return bucket != null && !bucket.isBlank();
		}

	}

}
