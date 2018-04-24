package com.serenegiant.janus.response;

public class PluginInfo {
	public final String name;
	public final String author;
	public final String description;
	public final String version_string;
	public final int version;
	
	public PluginInfo(final String name, final String author, final String description,
					  final String version_string, final int version) {

		this.name = name;
		this.author = author;
		this.description = description;
		this.version_string = version_string;
		this.version = version;
	}
}
