package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Destroy {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	
	public Destroy(@NonNull final String transaction,
		@NonNull final BigInteger session_id) {

		this.janus = "destroy";
		this.transaction = transaction;
		this.session_id = session_id;
	}
	
	public Destroy(@NonNull final Session session) {
		this(session.transaction, session.data.id);
	}
}
