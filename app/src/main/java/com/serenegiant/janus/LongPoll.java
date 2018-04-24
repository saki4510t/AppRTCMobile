package com.serenegiant.janus;

import com.serenegiant.janus.response.Event;

import java.math.BigInteger;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface LongPoll {
	@GET("/janus/{session_id}")
	public Call<Event> getEvent(
		@Path("session_id") final BigInteger sessionId);
}
