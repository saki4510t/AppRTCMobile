package com.serenegiant.janus.response;

import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Arrays;

public class EventRoom {
	public final String janus;
	public final String sender;
	public final String transaction;
	public final PluginData plugindata;
	public final JSONObject jsep;
	
	public EventRoom(final String janus, final String sender,
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
		public final Data data;
		
		public PluginData(final String plugin, final Data data) {
			this.plugin = plugin;
			this.data = data;
		}
		
		@Override
		public String toString() {
			return "PluginData{" +
				"plugin='" + plugin + '\'' +
				", data=" + data +
				'}';
		}
	}
	
	public static class Data {
		public final String videoroom;
		public final int room;
		public final String description;
		public final BigInteger id;
		public final BigInteger private_id;
		public final String[] publishers;
		
		public Data(final String videoroom, final int room,
			final String description,
			final BigInteger id, final BigInteger private_id,
			final String[] publishers) {
			this.videoroom = videoroom;
			this.room = room;
			this.description = description;
			this.id = id;
			this.private_id = private_id;
			this.publishers = publishers;
		}
		
		@Override
		public String toString() {
			return "Data{" +
				"videoroom='" + videoroom + '\'' +
				", room=" + room +
				", description='" + description + '\'' +
				", id=" + id +
				", private_id=" + private_id +
				", publishers=" + Arrays.toString(publishers) +
				'}';
		}
	}
	
	@Override
	public String toString() {
		return "EventRoom{" +
			"janus='" + janus + '\'' +
			", sender='" + sender + '\'' +
			", transaction='" + transaction + '\'' +
			", plugindata=" + plugindata +
			", jsep=" + jsep +
			'}';
	}
}
