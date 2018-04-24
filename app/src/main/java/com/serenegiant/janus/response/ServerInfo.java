package com.serenegiant.janus.response;

import android.support.annotation.NonNull;

import java.util.List;

public class ServerInfo {
	public final String janus;
	public final String transaction;
	public final String name;
	public final int version;
	public final String version_string;
	public final String author;
	public final boolean data_channels;
	public final boolean ipv6;
	public final boolean ice_tcp;
	public final Transports transports;
	public final PluginInfos plugins;
	
	public ServerInfo(final String janus, final String transaction, final String name,
		final int version, final String version_string, final String author,
		final boolean data_channels, final boolean ipv6, final boolean ice_tcp,
		final Transports transports, final PluginInfos plugins) {

		this.janus = janus;
		this.transaction = transaction;
		this.name = name;
		this.version = version;
		this.version_string = version_string;
		this.author = author;
		this.data_channels = data_channels;
		this.ipv6 = ipv6;
		this.ice_tcp = ice_tcp;
		this.transports = transports;
		this.plugins = plugins;
	}

	public static class Transports {
		@NonNull
		public final List<Transport> transports;
		
		public Transports(@NonNull final List<Transport> transports) {
			this.transports = transports;
		}
	}

	public static class PluginInfos {
		@NonNull
		public final List<PluginInfo> plugins;
		
		public PluginInfos(@NonNull final List<PluginInfo> plugins) {
			this.plugins = plugins;
		}
	}
	
	public List<PluginInfo> plugins() {
		return plugins != null ? plugins.plugins : null;
	}
}
