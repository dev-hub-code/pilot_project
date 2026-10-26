package com.sealease.backend.document.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Documents' encrypted bytes in an S3 bucket, one object per document. Credentials come from the standard AWS
 * settings (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY in production).
 */
class S3DocumentStore implements AutoCloseable {

	private final S3Client s3;
	private final String bucket;

	S3DocumentStore(DocumentProperties.S3 properties) {
		this(S3Client.builder()
			.region(Region.of(properties.region()))
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.build(), properties.bucket());
	}

	S3DocumentStore(S3Client s3, String bucket) {
		this.s3 = s3;
		this.bucket = bucket;
	}

	void put(String key, byte[] encrypted) {
		s3.putObject(b -> b.bucket(bucket).key(key).contentType("application/octet-stream")
			.serverSideEncryption(ServerSideEncryption.AES256), RequestBody.fromBytes(encrypted));
	}

	byte[] get(String key) {
		return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
	}

	@Override
	public void close() {
		s3.close();
	}

}
