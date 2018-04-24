package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Hangup {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	
	public Hangup(@NonNull final String transaction,
		@NonNull final BigInteger session_id) {

		this.janus = "hangup";
		this.transaction = transaction;
		this.session_id = session_id;
	}
	
	public Hangup(@NonNull final Session session) {
		this(session.transaction, session.data.id);
	}
}
