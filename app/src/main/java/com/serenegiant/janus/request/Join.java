package com.serenegiant.janus.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.math.BigInteger;

/**
 * message body
 */
public class Join {
	public final String request;
	public final int room;
	public final String ptype;
	public final String display;
	public final BigInteger feed;
	
	public Join(final int room, @NonNull final String pType,
		@Nullable final String display,
		@Nullable final BigInteger feed) {

		this.request = "join";
		this.room = room;
		this.ptype = pType;
		this.display = display;
		this.feed = feed;
	}
	
	@Override
	public String toString() {
		return "Join{" +
			"request='" + request + '\'' +
			", room=" + room +
			", ptype='" + ptype + '\'' +
			", display='" + display + '\'' +
			", feed='" + feed + '\'' +
			'}';
	}
}
