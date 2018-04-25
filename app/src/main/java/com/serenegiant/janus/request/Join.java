package com.serenegiant.janus.request;

/**
 * message body
 */
public class Join {
	public final String request;
	public final int room;
	public final String ptype;
	public final String display;
	
	public Join(final int room, final String display) {
		this.request = "join";
		this.room = room;
		this.ptype = "publisher";
		this.display = display;
	}
}
