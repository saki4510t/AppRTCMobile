package com.serenegiant.janus.response;

import java.math.BigInteger;

public class Session {

	public final String janus;
	public final String transaction;
	public final Data data;
	
	public Session(final String janus, final String transaction, final Data data) {
		this.janus = janus;
		this.transaction = transaction;
		this.data = data;
	}
	
	public static class Data {
		public final BigInteger id;
		
		public Data(final BigInteger id) {
			this.id = id;
		}
	}
	
	public BigInteger id() {
		return data != null ? data.id : null;
	}
	
	@Override
	public String toString() {
		return "Session{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			", id=" + id() +
			'}';
	}
}
