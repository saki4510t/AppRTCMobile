package com.serenegiant.janus.response;

public class SendResponse {
	public final String janus;
	public final String transaction;
	
	public SendResponse(final String janus, final String transaction) {
		this.janus = janus;
		this.transaction = transaction;
	}
	
	@Override
	public String toString() {
		return "SendResponse{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			'}';
	}
}
