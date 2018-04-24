package com.serenegiant.janus;

import com.serenegiant.janus.request.Detach;
import com.serenegiant.janus.request.Hangup;
import com.serenegiant.janus.request.Message;
import com.serenegiant.janus.response.Response;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface VideoRoom extends Janus {

	@POST("/{session_id}/{plugin_id}")
	public Call<Response> send(
		@Path("session_id") final String sessionId,
		@Path("plugin_id") final String pluginId,
		@Body final Message message);
	
	@POST("/{session_id}/{plugin_id}")
	public Call<Void> detach(
		@Path("session_id") final String sessionId,
		@Path("plugin_id") final String pluginId,
		@Body final Detach detach);
	
	@POST("/{session_id}/{plugin_id}")
	public Call<Void> hangup(
		@Path("session_id") final String sessionId,
		@Path("plugin_id") final String pluginId,
		@Body final Hangup hangup);
	
}
