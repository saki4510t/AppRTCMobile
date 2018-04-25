package com.serenegiant.janus.request;

/**
 * message body
 */
public class Configure {
	public final String request;
	public final boolean audio;
	public final boolean video;
	
	public Configure(final boolean audio, final boolean video) {
		this.request = "configure";
		this.audio = audio;
		this.video = video;
	}
	
	@Override
	public String toString() {
		return "Configure{" +
			"request='" + request + '\'' +
			", audio=" + audio +
			", video=" + video +
			'}';
	}
}
