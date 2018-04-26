package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

import com.serenegiant.janus.Room;

import org.webrtc.IceCandidate;

import java.math.BigInteger;

public class Trickle {
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

	public Trickle(@NonNull final BigInteger session_id,
		@NonNull final BigInteger handle_id,
		@NonNull final Candidate candidate) {

		this.janus = "trickle";
		this.transaction = TransactionGenerator.get(12);
		this.session_id = session_id;
		this.handle_id = handle_id;
		this.candidate = candidate;
	}
	
	public Trickle(@NonNull final Room room,
	   @NonNull final Candidate candidate) {
	   
	   this(room.sessionId, room.pluginId, candidate);
	}
	
	public Trickle(@NonNull final Room room,
		@NonNull final IceCandidate candidate) {

		this(room.sessionId, room.pluginId,
			new Candidate(candidate.sdpMLineIndex,
				candidate.sdpMid, candidate.sdp));
	}

	public static class Candidate {
		public final int sdpMLineIndex;
		public final String sdpMid;
		public final String candidate;
		
		public Candidate(final int sdpMLineIndex, final String sdpMid,
			final String candidate) {

			this.sdpMLineIndex = sdpMLineIndex;
			this.sdpMid = sdpMid;
			this.candidate = candidate;
		}
		
		@Override
		public String toString() {
			return "Candidate{" +
				"sdpMLineIndex=" + sdpMLineIndex +
				", sdpMid='" + sdpMid + '\'' +
				", candidate='" + candidate + '\'' +
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
