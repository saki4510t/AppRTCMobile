package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.Session;

import org.json.JSONObject;

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
	
	public final JSONObject body;
	public final JSONObject jsep;
	
	public Message(@NonNull final BigInteger session_id,
		@NonNull final BigInteger handle_id,
		final JSONObject body, final JSONObject jsep) {

		this.janus = "message";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
		this.handle_id = handle_id;
		this.body = body;
		this.jsep = jsep;
	}

	public Message(@NonNull final BigInteger session_id,
		@NonNull final BigInteger handle_id,
		final JSONObject body) {

		this(session_id, handle_id, body, null);
	}

	public Message(@NonNull final Session session, @NonNull final Plugin plugin,
		final JSONObject body) {

		this(session.id(), plugin.id(), body, null);
	}

	public Message(@NonNull final Session session, @NonNull final Plugin plugin,
		final JSONObject body, final JSONObject jsep) {

		this(session.id(), plugin.id(), body, jsep);
	}
}
