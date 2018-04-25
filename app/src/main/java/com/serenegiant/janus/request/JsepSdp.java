package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

/**
 * offer時のjsep用
 */
public class JsepSdp {
	@NonNull
	public final String type;
	public final String sdp;
	
	public JsepSdp(@NonNull final String type, final String sdp) {
		this.type = type;
		this.sdp = sdp;
	}
}
