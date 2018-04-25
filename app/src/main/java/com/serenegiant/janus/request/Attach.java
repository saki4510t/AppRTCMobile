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
	
	public Attach(@NonNull final BigInteger session_id,
		@NonNull final String plugin) {
		
		this.janus = "attach";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
		this.plugin = plugin;
	}
	
	public Attach(@NonNull final Session session, @NonNull final String plugin) {
		this(session.id(), plugin);
	}
	
	@Override
	public String toString() {
		return "Attach{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			", session_id=" + session_id +
			", plugin='" + plugin + '\'' +
			'}';
	}
}
