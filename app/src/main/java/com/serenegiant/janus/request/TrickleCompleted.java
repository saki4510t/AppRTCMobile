package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.Room;

import org.webrtc.IceCandidate;

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
		@NonNull final BigInteger handle_id) {

		this.janus = "trickle";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
		this.handle_id = handle_id;
		this.candidate = new Candidate();
	}
	
	public TrickleCompleted(@NonNull final Room room) {
	   
	   this(room.sessionId, room.pluginId);
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
