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
	public final String commit_hash;
	public final String compile_time;
	public final boolean data_channels;
	public final int session_timeout;
	public final boolean ipv6;
	public final boolean ice_tcp;
	public final Transports transports;
	public final PluginInfos plugins;
	
	public ServerInfo(final String janus, final String transaction, final String name,
		final int version, final String version_string, final String author,
		final String commit_hash, final String compile_time,
		final boolean data_channels, final int session_timeout,
		final boolean ipv6, final boolean ice_tcp,
		final Transports transports, final PluginInfos plugins) {

		this.janus = janus;
		this.transaction = transaction;
		this.name = name;
		this.version = version;
		this.version_string = version_string;
		this.author = author;
		this.commit_hash = commit_hash;
		this.compile_time = compile_time;
		this.data_channels = data_channels;
		this.session_timeout = session_timeout;
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
		
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("Transports{");
			if ((transports != null) && (transports.size() > 0)) {
				for (final Transport transport: transports) {
					sb.append(transport).append(",");
				}
			}
			sb.append('}');
			return sb.toString();
		}
	}

	public static class PluginInfos {
		@NonNull
		public final List<PluginInfo> plugins;
		
		public PluginInfos(@NonNull final List<PluginInfo> plugins) {
			this.plugins = plugins;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("PluginInfos{");
			if ((plugins != null) && (plugins.size() > 0)) {
				for (final PluginInfo plugin: plugins) {
					sb.append(plugin).append(",");
				}
			}
			sb.append('}');
			return sb.toString();
		}
	}
	
	public List<PluginInfo> plugins() {
		return plugins != null ? plugins.plugins : null;
	}

	@Override
	public String toString() {
		return "ServerInfo{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			", name='" + name + '\'' +
			", version=" + version +
			", version_string='" + version_string + '\'' +
			", author='" + author + '\'' +
			", data_channels=" + data_channels +
			", ipv6=" + ipv6 +
			", ice_tcp=" + ice_tcp +
			", transports=" + transports +
			", plugins=" + plugins +
			'}';
	}
	
}
