package com.serenegiant.janus.response;

import org.json.JSONObject;

public class Event {
	public final String janus;
	public final String sender;
	public final String transaction;
	public final PluginData plugindata;
	public final JSONObject jsep;
	
	public Event(final String janus, final String sender,
		final String transaction,
		final PluginData plugindata, final JSONObject jsep) {
		
		this.janus = janus;
		this.sender = sender;
		this.transaction = transaction;
		this.plugindata = plugindata;
		this.jsep = jsep;
	}
	
	public static class PluginData {
		public final String plugin;
		public final JSONObject data;
		
		public PluginData(final String plugin, final JSONObject data) {
			this.plugin = plugin;
			this.data = data;
		}
	}
}
