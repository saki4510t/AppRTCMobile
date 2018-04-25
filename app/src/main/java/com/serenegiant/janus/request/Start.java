package com.serenegiant.janus.request;

public class Start {
	public final String request;
	public final int room;
	
	public Start(final int room) {
		this.request = "start";
		this.room = room;
	}
	
	@Override
	public String toString() {
		return "Start{" +
			"request='" + request + '\'' +
			", room=" + room +
			'}';
	}
}
