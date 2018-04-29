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
	
	/**
	 * attach to VideoRoom plugin
	 */
	/*package*/ void attach() {
		if (DEBUG) Log.v(TAG, "attach:");
		final Attach attach = new Attach(mSession,
			"janus.plugin.videoroom",
			mTransactionCallback);
		final Call<Plugin> call = mVideoRoom.attach(mSession.id(), attach);
		addCall(call);
		call.enqueue(new Callback<Plugin>() {
			@Override
			public void onResponse(@NonNull final Call<Plugin> call,
				@NonNull final Response<Plugin> response) {

				if (response.isSuccessful() && (response.body() != null)) {
					removeCall(call);
					mPlugin = response.body();
					mRoom = new Room(mSession, mPlugin);
					mRoomState = RoomState.ATTACHED;
					// プラグインにアタッチできた＼(^o^)／
					if (DEBUG) Log.v(TAG, "attach#onResponse:" + mRoom);
					TransactionManager.removeTransaction(attach.transaction);
					mCallback.onAttach(JanusPlugin.this);
					// ルームへjoin
						handler.post(() -> {
							join();
					});
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
	
	@NonNull
	protected abstract String getPType();

	protected BigInteger getFeedId() {
		return null;
	}

	/**
	 * join Room
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
			if (DEBUG) Log.v(TAG, "join:response=" + response + "\n" + response.body());
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				final EventRoom join = response.body();
				if ("event".equals(join.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
					handleOnJoin(join);
					TransactionManager.removeTransaction(message.transaction);
				} else if (!"ack".equals(join.janus)
					&& !"keepalive".equals(join.janus)) {

					TransactionManager.removeTransaction(message.transaction);
					throw new RuntimeException("unexpected response:" + response);
				}
			} else {
				throw new RuntimeException("unexpected response:" + response);
			}
		} catch (final Exception e) {
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

		if (DEBUG) Log.v(TAG, "onReceived:" + body);
		final String janus = body.optString("janus");
		boolean handled = false;
		if (!TextUtils.isEmpty(janus)) {
			switch (janus) {
			case "ack":
				// do nothing
				break;
			case "keepalive":
				// サーバー側がタイムアウト(30秒？)した時は{"janus": "keepalive"}が来る
				// do nothing
				break;
			case "event":
				// プラグインイベント
				handled = handlePluginEvent(body);
				break;
			case "media":
			case "webrtcup":
			case "slowlink":
			case "hangup":
				// event for WebRTC
				handled = handleWebRTCEvent(body);
				break;
			case "error":
				reportError(new RuntimeException("error response " + body));
				return true;
			default:
				Log.d(TAG, "handleLongPoll:unknown event:" + body);
				break;
			}
		}
		if (handled) {
			// FIXME ここで削除してしまっていいいかどうか要確認
			TransactionManager.removeTransaction(transaction);
		}
		return handled;	// true: handled
	}

	/**
	 * プラグイン向けのイベントメッセージの処理
	 * @param body
	 * @return
	 */
	protected boolean handlePluginEvent(final JSONObject body) {
		if (DEBUG) Log.v(TAG, "handlePluginEvent:" + body);
		final Gson gson = new Gson();
		final EventRoom event = gson.fromJson(body.toString(), EventRoom.class);
		// XXX このsenderはPublisherとして接続したときのVideoRoomプラグインのidらしい
		final BigInteger sender = event.sender;
		final String eventType = (event.plugindata != null) && (event.plugindata.data != null)
			? event.plugindata.data.videoroom : null;
		if (DEBUG) Log.v(TAG, "handlePluginEvent:" + event);
		if (!TextUtils.isEmpty(eventType)) {
			switch (eventType) {
			case "joined":
				return handlePluginEventJoined(event);
			case "event":
				return handlePluginEventEvent(event);
			}
		}
		return false;	// true: handled
	}
	
	/**
	 * eventTypeが"joined"のときの処理
	 * @param event
	 * @return
	 */
	protected boolean handlePluginEventJoined(@NonNull final EventRoom event) {
		handleOnJoin(event);
		return true;	// true: 処理済み
	}
	
	/**
	 * eventTypeが"event"のときの処理
	 * @param event
	 * @return
	 */
	protected boolean handlePluginEventEvent(@NonNull final EventRoom event) {
		if (event.jsep != null) {
			if ("answer".equals(event.jsep.type)) {
				final SessionDescription answerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("answer"),
						event.jsep.sdp);
				return onRemoteDescription(answerSdp);
			} else if ("offer".equals(event.jsep.type)) {
				final SessionDescription offerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("offer"),
						event.jsep.sdp);
				return onRemoteDescription(offerSdp);
			}
		}
		// FIXME remote candidateの処理がどっかに要る気がするんだけど
//		IceCandidate remoteCandidate = null;
//		// FIXME removeCandidateを生成する
//		if (remoteCandidate != null) {
//			events.onRemoteIceCandidate(remoteCandidate);
//		} else {
//			// FIXME remoteCandidateがなかった時
//		}
		return true;	// true: 処理済み
	}
	
	/**
	 * リモート側のSessionDescriptionの準備ができたときの処理
	 * @param sdp
	 * @return
	 */
	protected boolean onRemoteDescription(@NonNull final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "onRemoteDescription:");
		mRemoteSdp = sdp;
		return true;	// FIXME 本当はfalseなんだけど今はJanusRESTRTCClient側の処理が残っているのでtrueを返す
	}

	/**
	 * WebRTC関係のイベント受信時の処理
	 * @param body
	 * @return
	 */
	protected boolean handleWebRTCEvent(final JSONObject body) {
		if (DEBUG) Log.v(TAG, "handleWebRTCEvent:" + body);
		return false;	// true: handled
	}

	/**
	 * Join完了イベント受信時の処理
	 * @param room
	 */
	protected void handleOnJoin(final EventRoom room) {
		if (DEBUG) Log.v(TAG, "handleOnJoin:" + room);
		mRoomState = RoomState.CONNECTED;
		// FIXME 未実装
		mRoom.publisherId = room.plugindata.data.id;
		checkPublishers(room);

		mCallback.onJoin(this, room);
	}

	protected void checkPublishers(final EventRoom room) {
		if (DEBUG) Log.v(TAG, "checkPublishers:");
		if ((room.plugindata != null)
			&& (room.plugindata.data != null)) {

			@NonNull
			final List<PublisherInfo> changed = mRoom.updatePublisher(room.plugindata.data.publishers);
			if (!changed.isEmpty()) {
				if (DEBUG) Log.v(TAG, "checkPublishers:number of publishers changed");
				// FIXME EventRoom#plugindata#data#publishersが変化した時になんかせなあかんのかも
				// FIXME Subscriberの生成＆attach処理が必要
			}
		}
	}
//--------------------------------------------------------------------------------
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
		protected boolean handlePluginEventEvent(@NonNull final EventRoom event) {
			super.handlePluginEventEvent(event);
			checkPublishers(event);
			return true;
		}
	
		public void sendLocalIceCandidate(final IceCandidate candidate, final boolean isLoopback) {
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
				if (DEBUG) Log.v(TAG, "sendLocalIceCandidateInternal:response=" + response
					+ "\n" + response.body());
				if (response.isSuccessful() && (response.body() != null)) {
					removeCall(call);
					final EventRoom join = response.body();
					if ("event".equals(join.janus)) {
						if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
//						// FIXME 正常に処理できた…Roomの情報を更新する
//						IceCandidate remoteCandidate = null;
//						// FIXME removeCandidateを生成する
//						if (remoteCandidate != null) {
//							mCallback.onRemoteIceCandidate(this, remoteCandidate);
//						} else {
//							// FIXME remoteCandidateがなかった時
//						}
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
			@NonNull final SessionDescription sdp) {

			super(videoRoom, session, callback);
			if (DEBUG) Log.v(TAG, "Subscriber:");
			this.feederId = feederId;
			this.mLocalSdp = sdp;
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
			super.onRemoteDescription(sdp);
			// 通話準備完了
			mCallback.onRemoteDescription(this, sdp);
			return true;
		}

	}
}
