package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.Room;

import java.math.BigInteger;

public class Message {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	@NonNull
	public final BigInteger handle_id;
	
	public final Object body;
	public final Object jsep;
	
	public Message(@NonNull final BigInteger session_id,
		@NonNull final BigInteger handle_id,
		final Object body, final Object jsep) {

		this.janus = "message";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
		this.handle_id = handle_id;
		this.body = body;
		this.jsep = jsep;
	}

	public Message(@NonNull final BigInteger session_id,
		@NonNull final BigInteger handle_id,
		final Object body) {

		this(session_id, handle_id, body, null);
	}

	public Message(@NonNull final Room room, final Object body) {

		this(room.sessionId, room.pluginId, body, null);
	}

	public Message(@NonNull final Room room,
		final Object body, final Object jsep) {

		this(room.sessionId, room.pluginId, body, jsep);
	}
	
	@Override
	public String toString() {
		return "Message{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			", session_id=" + session_id +
			", handle_id=" + handle_id +
			", body=" + body +
			", jsep=" + jsep +
			'}';
	}
}
