package com.serenegiant.janus.request;

import java.util.Random;

public class TransactionGenerator {
	private static class RandomString {
		final String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		final Random rnd = new Random();

		public String get(int length) {
			final StringBuilder sb = new StringBuilder(length);
			for (int i = 0; i < length; i++) {
				sb.append(str.charAt(rnd.nextInt(str.length())));
			}
			return sb.toString();
		}
	}

	private static final RandomString mRandomString = new RandomString();

	public static String get(int length) {
		return mRandomString.get(length);
	}
}
