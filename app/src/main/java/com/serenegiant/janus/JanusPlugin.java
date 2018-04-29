package com.serenegiant.janus;

import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.serenegiant.janus.request.Attach;
import com.serenegiant.janus.request.Configure;
import com.serenegiant.janus.request.Detach;
import com.serenegiant.janus.request.Join;
import com.serenegiant.janus.request.JsepSdp;
import com.serenegiant.janus.request.Message;
import com.serenegiant.janus.request.Start;
import com.serenegiant.janus.request.Trickle;
import com.serenegiant.janus.request.TrickleCompleted;
import com.serenegiant.janus.response.EventRoom;
import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.PublisherInfo;
import com.serenegiant.janus.response.Session;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/*package*/ abstract class JanusPlugin {
	private static final boolean DEBUG = true;	// set false on production
	
	/**
	 * callback interface for JanusPlugin
	 */
	/*package*/ interface JanusPluginCallback {
		public void onAttach(@NonNull final JanusPlugin plugin);
		public void onJoin(@NonNull final JanusPlugin plugin, final EventRoom room);
		public void onDetach(@NonNull final JanusPlugin plugin);
		public void onLeave(@NonNull final JanusPlugin plugin, @NonNull final BigInteger pluginId);
		public void onRemoteIceCandidate(@NonNull final JanusPlugin plugin,
			final IceCandidate remoteCandidate);
		/**
		 * リモート側のSessionDescriptionを受信した時
		 * これを呼び出すと通話中の状態になる
		 * @param plugin
		 * @param sdp
		 */
		public void onRemoteDescription(@NonNull final JanusPlugin plugin,
			final SessionDescription sdp);

		public void onError(@NonNull final JanusPlugin plugin,
			@NonNull final Throwable t);
	}
	
	private static enum RoomState {
		UNINITIALIZED,
		ATTACHED,
		CONNECTED,
		CLOSED,
		ERROR }

	protected final String TAG = "JanusPlugin:" + getClass().getSimpleName();
	@NonNull
	protected final VideoRoom mVideoRoom;
	@NonNull
	protected final Session mSession;
	@NonNull
	protected final JanusPluginCallback mCallback;
	protected final Handler handler;
	protected final List<Call<?>> mCurrentCalls = new ArrayList<>();
	protected RoomState mRoomState = RoomState.UNINITIALIZED;
	protected Plugin mPlugin;
	protected Room mRoom;
	protected SessionDescription mLocalSdp;
	protected SessionDescription mRemoteSdp;
	
	/**
	 * constructor
	 * @param session
	 * @param callback
	 */
	/*package*/ JanusPlugin(@NonNull VideoRoom videoRoom,
		@NonNull final Session session,
		@NonNull final JanusPluginCallback callback) {
		
		this.mVideoRoom = videoRoom;
		this.mSession = session;
		this.mCallback = callback;
		this.handler = new Handler();
	}

	BigInteger id() {
		return mPlugin != null ? mPlugin.id() : null;
	}
	
	@NonNull
	protected abstract String getPType();

	protected BigInteger getFeedId() {
		return null;
	}

	/**
	 * attach to VideoRoom plugin
	 */
	/*package*/ void attach() {
		if (DEBUG) Log.v(TAG, "attach:");
		final Attach attach = new Attach(mSession,
			"janus.plugin.videoroom",
			null);
		final Call<Plugin> call = mVideoRoom.attach(mSession.id(), attach);
		addCall(call);
		call.enqueue(new Callback<Plugin>() {
			@Override
			public void onResponse(@NonNull final Call<Plugin> call,
				@NonNull final Response<Plugin> response) {

				if (response.isSuccessful() && (response.body() != null)) {
					removeCall(call);
					final Plugin plugin = response.body();
					if ("success".equals(plugin.janus)) {
						mPlugin = plugin;
						mRoom = new Room(mSession, mPlugin);
						mRoomState = RoomState.ATTACHED;
						// プラグインにアタッチできた＼(^o^)／
						if (DEBUG) Log.v(TAG, "attach:success");
						mCallback.onAttach(JanusPlugin.this);
						// ルームへjoin
						handler.post(() -> {
							join();
						});
					} else {
						reportError(new RuntimeException("unexpected response:" + response));
					}
				} else {
					reportError(new RuntimeException("unexpected response:" + response));
				}
			}
			
			@Override
			public void onFailure(@NonNull final Call<Plugin> call,
				@NonNull final Throwable t) {

				reportError(t);
			}
		});
	}
	
	/**
	 * join to Room
	 * @throws IOException
	 */
	/*package*/ void join() {
		if (DEBUG) Log.v(TAG, "join:");
		final Message message = new Message(mRoom,
			new Join(1234/*FIXME*/, getPType(), Build.MODEL, getFeedId()),
			mTransactionCallback);
		if (DEBUG) Log.v(TAG, "join:" + message);
		final Call<EventRoom> call = mVideoRoom.join(mSession.id(), mPlugin.id(), message);
		addCall(call);
		try {
			final Response<EventRoom> response = call.execute();
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				final EventRoom join = response.body();
				if ("event".equals(join.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
					handlePluginEventJoined(message.transaction, join);
				} else if (!"ack".equals(join.janus)
					&& !"keepalive".equals(join.janus)) {

					throw new RuntimeException("unexpected response:" + response);
				}
				// 実際の応答はlong pollで待機
			} else {
				throw new RuntimeException("unexpected response:" + response);
			}
		} catch (final Exception e) {
			TransactionManager.removeTransaction(message.transaction);
			cancelCall();
			detach();
			reportError(e);
		}
	}
	
	/**
	 * detach from VideoRoom plugin
	 */
	/*package*/ void detach() {
		if (DEBUG) Log.v(TAG, "detach:");
		if ((mRoomState == RoomState.CONNECTED)
			|| (mRoomState == RoomState.ATTACHED)
			|| (mPlugin != null)) {

			cancelCall();
			final Call<Void> call = mVideoRoom.detach(mSession.id(), mPlugin.id(),
				new Detach(mSession, mTransactionCallback));
			addCall(call);
			try {
				call.execute();
			} catch (final IOException e) {
				if (DEBUG) Log.w(TAG, e);
			}
			removeCall(call);
		}
		mRoomState = RoomState.CLOSED;
		mRoom = null;
		mPlugin = null;
	}

	/*package*/ void sendOfferSdp(final SessionDescription sdp, final boolean isLoopback) {
		if (DEBUG) Log.v(TAG, "sendOfferSdp:");
		if (mRoomState != RoomState.CONNECTED) {
			reportError(new RuntimeException("Sending offer SDP in non connected state."));
			return;
		}
		final Call<EventRoom> call = mVideoRoom.offer(
			mSession.id(),
			mPlugin.id(),
			new Message(mRoom,
				new Configure(true, true),
				new JsepSdp("offer", sdp.description),
				mTransactionCallback)
		);
		addCall(call);
		try {
			final Response<EventRoom> response = call.execute();
			if (DEBUG) Log.v(TAG, "sendOfferSdp:response=" + response
				+ "\n" + response.body());
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				this.mLocalSdp = sdp;
				final EventRoom offer = response.body();
				if ("event".equals(offer.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
					final SessionDescription answerSdp
						= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("answer"),
						offer.jsep.sdp);
					mCallback.onRemoteDescription(this, answerSdp);
				} else if (!"ack".equals(offer.janus)
					&& !"keepalive".equals(offer.janus)) {
					throw new RuntimeException("unexpected response " + response);
				}
				// 実際の待機はlong pollで行う
			} else {
				throw new RuntimeException("failed to send offer sdp");
			}
			if (isLoopback) {
				// In loopback mode rename this offer to answer and route it back.
				mCallback.onRemoteDescription(this, new SessionDescription(
					SessionDescription.Type.fromCanonicalForm("answer"),
					sdp.description));
			}
		} catch (final Exception e) {
			cancelCall();
			reportError(e);
		}
	}
	
	/*package*/ void sendAnswerSdp(final SessionDescription sdp, final boolean isLoopback) {
		if (DEBUG) Log.v(TAG, "sendAnswerSdpInternal:");
		if (isLoopback) {
			Log.e(TAG, "Sending answer in loopback mode.");
			return;
		}
		final Call<ResponseBody> call = mVideoRoom.send(
			mSession.id(),
			mPlugin.id(),
			new Message(mRoom,
				new Start(1234),
				new JsepSdp("answer", sdp.description),
				mTransactionCallback)
		);
		addCall(call);
		try {
			final Response<ResponseBody> response = call.execute();
			if (DEBUG) Log.v(TAG, "sendAnswerSdpInternal:response=" + response
				+ "\n" + response.body());
			removeCall(call);
		} catch (final IOException e) {
			cancelCall();
			reportError(e);
		}
	}

	public void sendLocalIceCandidate(final IceCandidate candidate, final boolean isLoopback) {
		if (DEBUG) Log.v(TAG, "sendLocalIceCandidate:");
		final Call<EventRoom> call;
		if (candidate != null) {
			call = mVideoRoom.trickle(
				mSession.id(),
				mPlugin.id(),
				new Trickle(mRoom, candidate, mTransactionCallback)
			);
		} else {
			call = mVideoRoom.trickleCompleted(
				mSession.id(),
				mPlugin.id(),
				new TrickleCompleted(mRoom, mTransactionCallback)
			);
		}
		addCall(call);
		try {
			final Response<EventRoom> response = call.execute();
//				if (DEBUG) Log.v(TAG, "sendLocalIceCandidate:response=" + response
//					+ "\n" + response.body());
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				final EventRoom join = response.body();
				if ("event".equals(join.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
//					// FIXME 正常に処理できた…Roomの情報を更新する
//					IceCandidate remoteCandidate = null;
//					// FIXME removeCandidateを生成する
//					if (remoteCandidate != null) {
//						mCallback.onRemoteIceCandidate(this, remoteCandidate);
//					} else {
//						// FIXME remoteCandidateがなかった時
//					}
				} else if (!"ack".equals(join.janus)
					&& !"keepalive".equals(join.janus)) {

					throw new RuntimeException("unexpected response " + response);
				}
				// 実際の待機はlong pollで行う
			} else {
				throw new RuntimeException("unexpected response " + response);
			}
			if ((candidate != null) && isLoopback) {
				mCallback.onRemoteIceCandidate(this, candidate);
			}
		} catch (final IOException e) {
			cancelCall();
			detach();
			reportError(e);
		}
	}

//--------------------------------------------------------------------------------
// Long pollによるメッセージ受信時の処理関係
	/**
	 * TransactionManagerからのコールバックインターフェースの実装
	 */
	protected final TransactionManager.TransactionCallback
		mTransactionCallback = new TransactionManager.TransactionCallback() {
	
		/**
		 * usually this is called from from long poll
		 * 実際の処理は上位クラスの#onReceivedへ移譲
		 * @param body
		 * @return
		 */
		@Override
		public boolean onReceived(@NonNull final String transaction,
			 final JSONObject body) {

			return JanusPlugin.this.onReceived(transaction, body);
		}
	};
	
	/**
	 * TransactionManagerからのコールバックの実際の処理
	 * @param body
	 * @return
	 */
	protected boolean onReceived(@NonNull final String transaction,
		final JSONObject body) {

		if (DEBUG) Log.v(TAG, "onReceived:");
		final String janus = body.optString("janus");
		boolean handled = false;
		if (!TextUtils.isEmpty(janus)) {
			switch (janus) {
			case "ack":
				// do nothing
				return true;
			case "keepalive":
				// サーバー側がタイムアウト(30秒？)した時は{"janus": "keepalive"}が来る
				// do nothing
				return true;
			case "event":
				// プラグインイベント
				handled = handlePluginEvent(transaction, body);
				break;
			case "media":
			case "webrtcup":
			case "slowlink":
			case "hangup":
				// event for WebRTC
				handled = handleWebRTCEvent(transaction, body);
				break;
			case "error":
				reportError(new RuntimeException("error response\n" + body));
				return true;
			default:
				Log.d(TAG, "handleLongPoll:unknown event\n" + body);
				break;
			}
		} else {
			Log.d(TAG, "handleLongPoll:unexpected response\n" + body);
		}
		return handled;	// true: handled
	}

	/**
	 * プラグイン向けのイベントメッセージの処理
	 * @param body
	 * @return
	 */
	protected boolean handlePluginEvent(@NonNull final String transaction,
		final JSONObject body) {

		if (DEBUG) Log.v(TAG, "handlePluginEvent:");
		final Gson gson = new Gson();
		final EventRoom event = gson.fromJson(body.toString(), EventRoom.class);
		// XXX このsenderはPublisherとして接続したときのVideoRoomプラグインのidらしい
		final BigInteger sender = event.sender;
		final String eventType = (event.plugindata != null) && (event.plugindata.data != null)
			? event.plugindata.data.videoroom : null;
		if (DEBUG) Log.v(TAG, "handlePluginEvent:" + event);
		if (!TextUtils.isEmpty(eventType)) {
			switch (eventType) {
			case "attached":
				return handlePluginEventAttached(transaction, event);
			case "joined":
				return handlePluginEventJoined(transaction, event);
			case "event":
				return handlePluginEventEvent(transaction, event);
			}
		}
		return false;	// true: handled
	}
	
	/**
	 * eventTypeが"attached"のときの処理
	 * Subscriberがリモート側へjoinした時のレスポンス
	 * @param room
	 * @return
	 */
	protected boolean handlePluginEventAttached(@NonNull final String transaction,
		@NonNull final EventRoom room) {

		if (DEBUG) Log.v(TAG, "handlePluginEventAttached:");
		// FIXME これが来たときはofferが一緒に来ているはずなのでanswerを送り返さないといけない
		if (room.jsep != null) {
			if ("answer".equals(room.jsep.type)) {
				if (DEBUG) Log.v(TAG, "handlePluginEventAttached:answer");
				// Janus-gatewayの相手している時にたぶんこれは来ない
				final SessionDescription answerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("answer"),
					room.jsep.sdp);
				return onRemoteDescription(answerSdp);
			} else if ("offer".equals(room.jsep.type)) {
				if (DEBUG) Log.v(TAG, "handlePluginEventAttached:offer");
				// Janus-gatewayの相手している時はたぶんいつもこっち
				final SessionDescription sdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("offer"),
					room.jsep.sdp);
				return onRemoteDescription(sdp);
			}
		}
		return true;
	}
	
	/**
	 * eventTypeが"joined"のときの処理
	 * @param room
	 * @return
	 */
	protected boolean handlePluginEventJoined(@NonNull final String transaction,
		@NonNull final EventRoom room) {

		if (DEBUG) Log.v(TAG, "handlePluginEventJoined:");
		mRoomState = RoomState.CONNECTED;
		mRoom.publisherId = room.plugindata.data.id;
		onJoin(room);
		return true;	// true: 処理済み
	}
	
	protected void onJoin(@NonNull final EventRoom room) {
		if (DEBUG) Log.v(TAG, "onJoin:");
		mCallback.onJoin(this, room);
	}

	/**
	 * eventTypeが"event"のときの処理
	 * @param room
	 * @return
	 */
	protected boolean handlePluginEventEvent(@NonNull final String transaction,
		@NonNull final EventRoom room) {

		if (DEBUG) Log.v(TAG, "handlePluginEventEvent:");
		if (room.jsep != null) {
			if ("answer".equals(room.jsep.type)) {
				final SessionDescription answerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("answer"),
					room.jsep.sdp);
				return onRemoteDescription(answerSdp);
			} else if ("offer".equals(room.jsep.type)) {
				final SessionDescription offerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("offer"),
					room.jsep.sdp);
				return onRemoteDescription(offerSdp);
			}
		}
		return true;	// true: 処理済み
	}
	
	/**
	 * リモート側のSessionDescriptionの準備ができたときの処理
	 * @param sdp
	 * @return
	 */
	protected boolean onRemoteDescription(@NonNull final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "onRemoteDescription:" + sdp);
		mRemoteSdp = sdp;
		return true;
	}

	/**
	 * WebRTC関係のイベント受信時の処理
	 * @param body
	 * @return
	 */
	protected boolean handleWebRTCEvent(@NonNull final String transaction,
		final JSONObject body) {

		if (DEBUG) Log.v(TAG, "handleWebRTCEvent:" + body);
		return false;	// true: handled
	}

//================================================================================
	/**
	 * set call that is currently in progress
	 * @param call
	 */
	protected void addCall(@NonNull final Call<?> call) {
		synchronized (mCurrentCalls) {
			mCurrentCalls.add(call);
		}
	}
	
	protected void removeCall(@NonNull final Call<?> call) {
		synchronized (mCurrentCalls) {
			mCurrentCalls.remove(call);
		}
		if (!call.isCanceled()) {
			try {
				call.cancel();
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
		}
	}
	
	/**
	 * cancel call if call is in progress
	 */
	protected void cancelCall() {
		synchronized (mCurrentCalls) {
			for (final Call<?> call: mCurrentCalls) {
				if ((call != null) && !call.isCanceled()) {
					try {
						call.cancel();
					} catch (final Exception e) {
						Log.w(TAG, e);
					}
				}
			}
			mCurrentCalls.clear();
		}
	}

	protected void reportError(@NonNull final Throwable t) {
		try {
			mCallback.onError(this, t);
		} catch (final Exception e) {
			Log.w(TAG, e);
		}
	}


//================================================================================
	/*package*/ static class Publisher extends JanusPlugin {

		/**
		 * コンストラクタ
		 * @param session
		 */
		/*package*/ Publisher(@NonNull VideoRoom videoRoom,
			@NonNull final Session session,
			@NonNull final JanusPluginCallback callback) {

			super(videoRoom, session, callback);
			if (DEBUG) Log.v(TAG, "Publisher:");
		}
		
		@NonNull
		@Override
		protected String getPType() {
			return "publisher";
		}
	
		@Override
		protected boolean handlePluginEventEvent(@NonNull final String transaction,
			@NonNull final EventRoom event) {

			super.handlePluginEventEvent(transaction, event);
			checkPublishers(event);
			return true;
		}
	
		@Override
		protected boolean onRemoteDescription(@NonNull final SessionDescription sdp) {
			if (DEBUG) Log.v(TAG, "onRemoteDescription:");
			super.onRemoteDescription(sdp);
//			// 通話準備完了
//			mCallback.onRemoteDescription(this, sdp);
			return true;
		}
	
		private void checkPublishers(final EventRoom room) {
			if (DEBUG) Log.v(TAG, "checkPublishers:");
			if ((room.plugindata != null)
				&& (room.plugindata.data != null)) {
	
				@NonNull
				final List<PublisherInfo> changed = mRoom.updatePublisher(room.plugindata.data.publishers);
				if (room.plugindata.data.leaving != null) {
					for (final PublisherInfo info: changed) {
						if (room.plugindata.data.leaving.equals(info.id)) {
							// XXX ここで削除できたっけ?
							changed.remove(info);
						}
					}
					// FIXME 存在しなくなったPublisherの処理
				}
				if (!changed.isEmpty()) {
					if (DEBUG) Log.v(TAG, "checkPublishers:number of publishers changed");
					for (final PublisherInfo info: changed) {
						handler.post(() -> {
							if (DEBUG) Log.v(TAG, "checkPublishers:attach new Subscriber");
							final Subscriber subscriber = new Subscriber(mVideoRoom,
								mSession, mCallback, info.id, mLocalSdp, mRemoteSdp);
							subscriber.attach();
						});
					}
				}
			}
		}
	}
	
	/*package*/ static class Subscriber extends JanusPlugin {
		/*package*/ final BigInteger feederId;

		/**
		 * コンストラクタ
		 * @param session
		 */
		/*package*/ Subscriber(@NonNull VideoRoom videoRoom,
			@NonNull final Session session,
			@NonNull final JanusPluginCallback callback,
			@NonNull final BigInteger feederId,
			@NonNull final SessionDescription localSdp,
			@NonNull final SessionDescription remoteSdp) {

			super(videoRoom, session, callback);
			if (DEBUG) Log.v(TAG, "Subscriber:local=" + localSdp + ",remote=" + remoteSdp);
			this.feederId = feederId;
			this.mLocalSdp = localSdp;
			this.mRemoteSdp = remoteSdp;
		}
		
		@NonNull
		@Override
		protected String getPType() {
			return "subscriber";
		}

		protected BigInteger getFeedId() {
			return feederId;
		}

		@Override
		protected boolean onRemoteDescription(@NonNull final SessionDescription sdp) {
			if (DEBUG) Log.v(TAG, "onRemoteDescription:\n" + sdp.description);
			if (DEBUG) Log.v(TAG, "onRemoteDescription:local:\n" + mLocalSdp.description);
			if (DEBUG) Log.v(TAG, "onRemoteDescription:remote:\n" + mRemoteSdp.description);
//			super.onRemoteDescription(sdp);
			if (sdp.type == SessionDescription.Type.OFFER) {
				sendAnswerSdp(mLocalSdp, false);
			}
//			// 通話準備完了
			mCallback.onRemoteDescription(this, mRemoteSdp);
			return true;
		}

	}
}
