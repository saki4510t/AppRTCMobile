package com.serenegiant.janus;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.PublisherInfo;
import com.serenegiant.janus.response.Session;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	 * EventRoom.plugindata.data.idの値
	 * publisherとしての自id
	 */
	public BigInteger publisherId;
	
	private final List<PublisherInfo> publishers
		= new ArrayList<>();

	public Room(@NonNull final Session session, @NonNull final Plugin plugin) {
		this.sessionId = session.id();
		this.pluginId = plugin.id();
	}
	
	/**
	 * Publisherをセット
	 * @param newPublishers
	 * @return 追加または削除されたPublisherのリスト
	 */
	@NonNull
	public List<PublisherInfo> updatePublisher(
		@Nullable final PublisherInfo[] newPublishers) {

		final List<PublisherInfo> result;
		if (newPublishers != null) {
			result = Arrays.asList(newPublishers);
			final List<PublisherInfo> src = Arrays.asList(newPublishers);
			
			synchronized (this.publishers) {
				// 既にRoomに登録されているPublisherを除く=未登録分
				result.removeAll(this.publishers);
				// 既にRoomに登録されているものから新しいPublisherを除く=削除分
				this.publishers.removeAll(src);
				result.addAll(this.publishers);
				this.publishers.clear();
				this.publishers.addAll(src);
			}
		} else {
			synchronized (this.publishers) {
				result = new ArrayList<>(this.publishers);
				this.publishers.clear();
			}
		}
		return result;
	}
}
