package com.serenegiant.janus.request;

/**
 * offer時のjsep用
 */
public class JsepSdp {
	public final String type;
	public final String sdp;
	
	public JsepSdp(final String sdp) {
		this.type = "offer";
		this.sdp = sdp;
	}
}
