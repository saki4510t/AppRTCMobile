package com.serenegiant.janus;

import java.math.BigInteger;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface LongPoll {
	@GET("/janus/{session_id}")
	public Call<ResponseBody> getEvent(
		@Path("session_id") final BigInteger sessionId);
}
