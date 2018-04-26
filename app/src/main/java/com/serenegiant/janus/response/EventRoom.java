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
		public final String videoroom;
		public final int room;
		public final String description;
		public final boolean configured;
		public final String audio_codec;
		public final String video_codec;
		public final BigInteger id;
		public final BigInteger private_id;
		public Publisher[] publishers;
		
		public Data(final String videoroom, final int room,
			final String description,
			final boolean configured,
			final String audio_codec, final String video_codec,
			final BigInteger id, final BigInteger private_id,
			final Publisher[] publishers) {

			this.videoroom = videoroom;
			this.room = room;
			this.description = description;
			this.configured = configured;
			this.audio_codec = audio_codec;
			this.video_codec = video_codec;
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
				", configured=" + configured +
				", audio_codec='" + audio_codec + '\'' +
				", video_codec='" + video_codec + '\'' +
				", id=" + id +
				", private_id=" + private_id +
				", publishers=" + Arrays.toString(publishers) +
				'}';
		}
	}
	
	public static class Publisher {
		public final BigInteger id;
		public final String display;
		public final String audio_codec;
		public final String video_codec;
		public final boolean talking;
		
		public Publisher(final BigInteger id,
			final String display,
			final String audio_codec, final String video_codec,
			final boolean talking) {

			this.id = id;
			this.display = display;
			this.audio_codec = audio_codec;
			this.video_codec = video_codec;
			this.talking = talking;
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