package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Attach {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	@NonNull
	public final String plugin;
	
	public Attach(@NonNull final String transaction,
		@NonNull final BigInteger session_id,
		@NonNull final String plugin) {
		
		this.janus = "attach";
		this.transaction = transaction;
		this.session_id = session_id;
		this.plugin = plugin;
	}
	
	public Attach(@NonNull final Session session, @NonNull final String plugin) {
		this(session.transaction, session.id(), plugin);
	}
}
