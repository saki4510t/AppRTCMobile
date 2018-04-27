package com.serenegiant.janus.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.serenegiant.janus.TransactionManager;
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
		@NonNull final String plugin,
		@Nullable TransactionManager.TransactionCallback callback) {
		
		this.janus = "attach";
		this.transaction = TransactionManager.get(12, callback);
		this.session_id = session_id;
		this.plugin = plugin;
	}
	
	public Attach(@NonNull final Session session,
		@NonNull final String plugin,
		@Nullable TransactionManager.TransactionCallback callback) {

		this(session.id(), plugin, callback);
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
