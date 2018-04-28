package com.serenegiant.janus.response;

import java.math.BigInteger;

public class PublisherInfo {
	public final BigInteger id;
	public final String display;
	public final String audio_codec;
	public final String video_codec;
	public final boolean talking;
	
	public PublisherInfo(final BigInteger id,
		final String display,
		final String audio_codec, final String video_codec,
		final boolean talking) {

		this.id = id;
		this.display = display;
		this.audio_codec = audio_codec;
		this.video_codec = video_codec;
		this.talking = talking;
	}
	
	/**
	 * 引数がPublisherの場合にidの比較のみを行う
	 * @param o
	 * @return
	 */
	@Override
	public boolean equals(final Object o) {

		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final PublisherInfo publisher = (PublisherInfo) o;
		
		return (id != null) && id.equals(publisher.id);
	}
	
	/**
	 * idのhashCodeを返す
	 * @return
	 */
	@Override
	public int hashCode() {
		return id != null ? id.hashCode() : super.hashCode();
	}
}
