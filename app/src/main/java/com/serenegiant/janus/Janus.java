package com.serenegiant.janus;

import com.serenegiant.janus.request.Attach;
import com.serenegiant.janus.request.Creator;
import com.serenegiant.janus.request.Destroy;
import com.serenegiant.janus.response.ServerInfo;
import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface Janus {
	@POST("/janus")
	public Call<Session> create(@Body final Creator create);

	@GET("/janus/info")
	public Call<ServerInfo> getInfo();

	@POST("/janus/{session_id}")
	public Call<Plugin> attach(
		@Path("session_id") final BigInteger sessionId,
		@Body final Attach attach);
	
	@POST("/janus/{session_id}")
	public Call<Void> destroy(
		@Path("session_id") final BigInteger sessionId,
		@Body final Destroy destroy);
}
