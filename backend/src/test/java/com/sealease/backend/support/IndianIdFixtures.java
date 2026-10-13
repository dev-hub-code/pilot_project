package com.sealease.backend.support;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Made-up but valid Indian identity numbers for tests. Each is random, because one Aadhaar number or PAN may only
 * belong to one account and tests share a database.
 */
public final class IndianIdFixtures {

	private static final int[][] MULTIPLY = {
			{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, { 1, 2, 3, 4, 0, 6, 7, 8, 9, 5 }, { 2, 3, 4, 0, 1, 7, 8, 9, 5, 6 },
			{ 3, 4, 0, 1, 2, 8, 9, 5, 6, 7 }, { 4, 0, 1, 2, 3, 9, 5, 6, 7, 8 }, { 5, 9, 8, 7, 6, 0, 4, 3, 2, 1 },
			{ 6, 5, 9, 8, 7, 1, 0, 4, 3, 2 }, { 7, 6, 5, 9, 8, 2, 1, 0, 4, 3 }, { 8, 7, 6, 5, 9, 3, 2, 1, 0, 4 },
			{ 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 } };
	private static final int[][] PERMUTE = {
			{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, { 1, 5, 7, 6, 2, 8, 3, 0, 9, 4 }, { 5, 8, 0, 3, 7, 9, 6, 1, 4, 2 },
			{ 8, 9, 1, 6, 0, 4, 3, 5, 2, 7 }, { 9, 4, 5, 3, 1, 2, 6, 8, 7, 0 }, { 4, 2, 8, 6, 5, 7, 3, 9, 0, 1 },
			{ 2, 7, 9, 3, 8, 0, 6, 4, 1, 5 }, { 7, 0, 4, 6, 9, 1, 3, 2, 5, 8 } };
	private static final int[] INVERSE = { 0, 4, 3, 2, 1, 5, 6, 7, 8, 9 };

	private IndianIdFixtures() {
	}

	/** 12 digits (shaped like a real one: not starting with 0 or 1, with a correct Verhoeff check digit). */
	public static String aadhaar() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		StringBuilder digits = new StringBuilder().append(random.nextInt(2, 10));
		for (int i = 0; i < 10; i++) {
			digits.append(random.nextInt(10));
		}
		int check = 0;
		for (int i = 0; i < digits.length(); i++) {
			check = MULTIPLY[check][PERMUTE[(i + 1) % 8][digits.charAt(digits.length() - 1 - i) - '0']];
		}
		return digits.append(INVERSE[check]).toString();
	}

	/** Five letters, four digits and a letter. */
	public static String pan() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		StringBuilder pan = new StringBuilder();
		for (int i = 0; i < 5; i++) {
			pan.append((char) ('A' + random.nextInt(26)));
		}
		pan.append(String.format("%04d", random.nextInt(10000)));
		return pan.append((char) ('A' + random.nextInt(26))).toString();
	}

}
