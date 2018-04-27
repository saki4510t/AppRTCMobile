package com.serenegiant.janus.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.serenegiant.janus.Room;
import com.serenegiant.janus.TransactionManager;

import java.math.BigInteger;

public class TrickleCompleted {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	@NonNull
	public final BigInteger session_id;
	@NonNull
	public final BigInteger handle_id;
	@NonNull
	public final Candidate candidate;

	public TrickleCompleted(@NonNull final BigInteger session_id,
		@NonNull final BigInteger handle_id,
		@Nullable final TransactionManager.TransactionCallback callback) {

		this.janus = "trickle";
		this.transaction = TransactionManager.get(12, callback);
		this.session_id = session_id;
		this.handle_id = handle_id;
		this.candidate = new Candidate();
	}
	
	public TrickleCompleted(@NonNull final Room room,
		@Nullable final TransactionManager.TransactionCallback callback) {

	   this(room.sessionId, room.pluginId, callback);
	}
	
	public static class Candidate {
		public final boolean completed;
		
		public Candidate() {
			this.completed = true;
		}
		
		@Override
		public String toString() {
			return "Candidate{" +
				"completed=" + completed +
				'}';
		}
	}

	@Override
	public String toString() {
		return "Trickle{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			", session_id=" + session_id +
			", candidate=" + candidate +
			'}';
	}
}
