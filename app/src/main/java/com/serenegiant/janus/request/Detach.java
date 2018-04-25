package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Detach {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	
	public Detach(@NonNull final BigInteger session_id) {

		this.janus = "destroy";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
	}
	
	public Detach(@NonNull final Session session) {
		this(session.id());
	}
}
