package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Session;

import org.json.JSONObject;

import java.math.BigInteger;

public class Trickle {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	@NonNull
	public final JSONObject candidates;

	public Trickle(@NonNull final BigInteger session_id,
		@NonNull final JSONObject candidates) {

		this.janus = "trickle";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
		this.candidates = candidates;
	}
	
	public Trickle(@NonNull final Session session,
	   @NonNull final JSONObject candidates) {
	   
	   this(session.id(), candidates);
	}
}
