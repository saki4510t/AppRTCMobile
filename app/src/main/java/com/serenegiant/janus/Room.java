package com.serenegiant.janus;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Room {
	@NonNull
	public final BigInteger sessionId;
	@NonNull
	public final BigInteger pluginId;
	
	public String state;
	public BigInteger id;

	public Room(@NonNull final Session session, @NonNull final Plugin plugin) {
		this.sessionId = session.id();
		this.pluginId = plugin.id();
	}
	
	@NonNull
	public String clientId() {
		return id != null ? id.toString() : "";
	}
}
