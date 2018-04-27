package com.serenegiant.janus.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.serenegiant.janus.TransactionManager;
import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Hangup {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	
	public Hangup(@NonNull final BigInteger session_id,
		@Nullable final TransactionManager.TransactionCallback callback) {

		this.janus = "hangup";
		this.transaction = TransactionManager.get(12, callback);
		this.session_id = session_id;
	}
	
	public Hangup(@NonNull final Session session,
		@NonNull final TransactionManager.TransactionCallback callback) {

		this(session.id(), callback);
	}
	
	@Override
	public String toString() {
		return "Hangup{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			", session_id=" + session_id +
			'}';
	}
}
