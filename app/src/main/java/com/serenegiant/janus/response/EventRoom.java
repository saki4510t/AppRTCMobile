package com.serenegiant.janus.response;

import com.serenegiant.janus.request.JsepSdp;

import java.math.BigInteger;
import java.util.Arrays;

public class EventRoom {
	public final String janus;
	public final String sender;
	public final String transaction;
	public final PluginData plugindata;
	public final JsepSdp jsep;
	
	public EventRoom(final String janus, final String sender,
		final String transaction,
		final PluginData plugindata, final JsepSdp jsep) {
		
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
		public String videoroom;
		public int room;
		public String description;
		public boolean configured;
		public String audio_codec;
		public String video_codec;
		public BigInteger id;
		public BigInteger private_id;
		public String[] publishers;
		
		@Override
		public String toString() {
			return "Data{" +
				"videoroom='" + videoroom + '\'' +
				", room=" + room +
				", description='" + description + '\'' +
				", configured=" + configured +
				", audio_codec='" + audio_codec + '\'' +
				", video_codec='" + video_codec + '\'' +
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
