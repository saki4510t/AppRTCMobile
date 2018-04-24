package com.serenegiant.janus.response;

public class Response {
	public final String janus;
	public final String transaction;
	
	public Response(final String janus, final String transaction) {
		this.janus = janus;
		this.transaction = transaction;
	}
}
