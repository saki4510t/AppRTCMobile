package com.serenegiant.janus;

import android.support.annotation.NonNull;

import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.Session;

import java.math.BigInteger;

public class Room {
	/**
	 * セッションId
	 */
	@NonNull
	public final BigInteger sessionId;

	/**
	 * プラグインId
	 */
	@NonNull
	public final BigInteger pluginId;
	
	/**
	 * 接続状態
 	 */
	public String state;
	
	/**
	 * クライアントID
	 */
	public BigInteger id;

	public Room(@NonNull final Session session, @NonNull final Plugin plugin) {
		this.sessionId = session.id();
		this.pluginId = plugin.id();
	}
	
	/**
	 * クライアントIdを取得
	 * @return
	 */
	@NonNull
	public String clientId() {
		return id != null ? id.toString() : "";
	}
}
