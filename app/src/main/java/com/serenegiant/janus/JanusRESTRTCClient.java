package com.serenegiant.janus;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.bind.DateTypeAdapter;
import com.serenegiant.janus.request.Attach;
import com.serenegiant.janus.request.Configure;
import com.serenegiant.janus.request.Creator;
import com.serenegiant.janus.request.Destroy;
import com.serenegiant.janus.request.Detach;
import com.serenegiant.janus.request.Join;
import com.serenegiant.janus.request.JsepSdp;
import com.serenegiant.janus.request.Message;
import com.serenegiant.janus.request.Start;
import com.serenegiant.janus.request.Trickle;
import com.serenegiant.janus.request.TrickleCompleted;
import com.serenegiant.janus.response.EventRoom;
import com.serenegiant.janus.response.Plugin;
import com.serenegiant.janus.response.ServerInfo;
import com.serenegiant.janus.response.Session;
import com.serenegiant.utils.HandlerThreadHandler;

import org.appspot.apprtc.AppRTCClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.serenegiant.janus.Const.*;

public class JanusRESTRTCClient implements AppRTCClient {
	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = JanusRESTRTCClient.class.getSimpleName();

	public interface JanusCallback {
		public void onConnectServer(@NonNull final JanusRESTRTCClient client);
		public List<PeerConnection.IceServer> getIceServers(@NonNull final JanusRESTRTCClient client);
	}

	private static enum ConnectionState {
		UNINITIALIZED,
		READY,	// janus-gateway server is ready to access
		NEW,
		ATTACHED,
		JOINED,
		CONNECTED,
		CLOSED,
		ERROR }

	private final Object mSync = new Object();
	private final WeakReference<Context> mWeakContext;
	private final String baseUrl;
	@NonNull
	private final SignalingEvents events;
	@NonNull
	private final JanusCallback mCallback;
	private VideoRoom mJanus;
	private LongPoll mLongPoll;
	private final List<Call<?>> mCurrentCalls = new ArrayList<>();
	private RoomConnectionParameters connectionParameters;
	private Handler handler;
	private ConnectionState roomState;
	private ServerInfo mServerInfo;
	private Session mSession;
	private Plugin mPlugin;
	private Room mRoom;

	public JanusRESTRTCClient(@NonNull final Context context,
		@NonNull final SignalingEvents events,
		@NonNull final JanusCallback callback,
		@NonNull final String baseUrl) {

		this.mWeakContext = new WeakReference<>(context);
		this.events = events;
		this.mCallback = callback;
		this.baseUrl = baseUrl;
		this.handler = HandlerThreadHandler.createHandler(TAG);
		this.roomState = ConnectionState.UNINITIALIZED;
	}
	
	@Override
	protected void finalize() throws Throwable {
		try {
			release();
		} finally {
			super.finalize();
		}
	}
	
	public void release() {
		disconnectFromRoom();
	}

//================================================================================
// implementations of org.appspot.apprtc.AppRTCClient interface
	@Override
	public void connectToRoom(final RoomConnectionParameters connectionParameters) {
		if (DEBUG) Log.v(TAG, "connectToRoom:");
		this.connectionParameters = connectionParameters;
		handler.post(new Runnable() {
			@Override
			public void run() {
				connectToRoomInternal();
			}
		});
	}
	
	@Override
	public void sendOfferSdp(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendOfferSdp:" + sdp);
		handler.post(new Runnable() {
			@Override
			public void run() {
				sendOfferSdpInternal(sdp);
			}
		});
	}
	
	@Override
	public void sendAnswerSdp(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendAnswerSdp:" + sdp);
		handler.post(new Runnable() {
			@Override
			public void run() {
				sendAnswerSdpInternal(sdp);
			}
		});
	}
	
	@Override
	public void sendLocalIceCandidate(final IceCandidate candidate) {
		if (DEBUG) Log.v(TAG, "sendLocalIceCandidate:");
		handler.removeCallbacks(mTrySendTrickleCompletedTask);
		handler.post(new Runnable() {
			@Override
			public void run() {
				sendLocalIceCandidateInternal(candidate);
			}
		});
		handler.postDelayed(mTrySendTrickleCompletedTask, 50);
	}
	
	private final Runnable mTrySendTrickleCompletedTask
		= new Runnable() {
		@Override
		public void run() {
			sendLocalIceCandidateInternal(null);
		}
	};
	
	@Override
	public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
		if (DEBUG) Log.v(TAG, "sendLocalIceCandidateRemovals:");
		handler.post(new Runnable() {
			@Override
			public void run() {
				// FIXME 未実装
//				final JSONObject json = new JSONObject();
//				jsonPut(json, "type", "remove-candidates");
//				final JSONArray jsonArray = new JSONArray();
//				for (final IceCandidate candidate : candidates) {
//					jsonArray.put(toJsonCandidate(candidate));
//				}
//				jsonPut(json, "candidates", jsonArray);
//				if (initiator) {
//					// Call initiator sends ice candidates to GAE server.
//					if (roomState != WebSocketRTCClient.ConnectionState.CONNECTED) {
//						reportError("Sending ICE candidate removals in non connected state.");
//						return;
//					}
//					sendPostMessage(WebSocketRTCClient.MessageType.MESSAGE, messageUrl, json.toString());
//					if (connectionParameters.loopback) {
//						events.onRemoteIceCandidatesRemoved(candidates);
//					}
//				} else {
//					// Call receiver sends ice candidates to websocket server.
//					wsClient.send(json.toString());
//				}
			}
		});
	}
	
	@Override
	public void disconnectFromRoom() {
		if (DEBUG) Log.v(TAG, "disconnectFromRoom:");
		cancelCall();
		handler.post(new Runnable() {
			@Override
			public void run() {
				disconnectFromRoomInternal();
				try {
					handler.getLooper().quit();
				} catch (final Exception e) {
					if (DEBUG) Log.w(TAG, e);
				}
			}
		});
	}

//================================================================================

	@Nullable
	private Context getContext() {
		return mWeakContext.get();
	}

	/**
	 * set call that is currently in progress
	 * @param call
	 */
	private void addCall(@NonNull final Call<?> call) {
		synchronized (mCurrentCalls) {
			mCurrentCalls.add(call);
		}
	}
	
	private void removeCall(@NonNull final Call<?> call) {
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
	private void cancelCall() {
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
	
	/**
	 * notify error
	 * @param t
	 */
	private void reportError(@NonNull final Throwable t) {
		Log.w(TAG, t);
		cancelCall();
		try {
			handler.post(new Runnable() {
				@Override
				public void run() {
					if (roomState != ConnectionState.ERROR) {
						roomState = ConnectionState.ERROR;
						events.onChannelError(t.getMessage());
					}
				}
			});
		} catch (final Exception e) {
			// ignore, will be already released.
		}
	}

	/**
	 * Connects to room - function runs on a local looper thread.
	 */
	private void connectToRoomInternal() {
		if (DEBUG) Log.v(TAG, "connectToRoomInternal:");
		// 通常のRESTアクセス用APIインターフェースを生成
		mJanus = setupRetrofit(
			setupHttpClient(HTTP_READ_TIMEOUT_MS, HTTP_WRITE_TIMEOUT_MS),
			baseUrl).create(VideoRoom.class);
		// long poll用APIインターフェースを生成
		mLongPoll = setupRetrofit(
			setupHttpClient(HTTP_READ_TIMEOUT_MS_LONG_POLL, HTTP_WRITE_TIMEOUT_MS),
			baseUrl).create(LongPoll.class);
		// Janus-gatewayサーバー情報を取得
		final Call<ServerInfo> call = mJanus.getInfo();
		addCall(call);
		call.enqueue(new Callback<ServerInfo>() {
			@Override
			public void onResponse(@NonNull final Call<ServerInfo> call,
				@NonNull final Response<ServerInfo> response) {
			
				if (response.isSuccessful() && (response.body() != null)) {
					removeCall(call);
					mServerInfo = response.body();
					if (DEBUG) Log.v(TAG, "connectToRoomInternal#onResponse:" + mServerInfo);
					handler.post(new Runnable() {
						@Override
						public void run() {
							createSession();
						}
					});
				} else {
					reportError(new RuntimeException("unexpected response:" + response));
				}
			}
			
			@Override
			public void onFailure(@NonNull final Call<ServerInfo> call,
				@NonNull final Throwable t) {

				reportError(t);
			}
		});
	}

	/**
	 * Disconnect from room and send bye messages - runs on a local looper thread.
	 */
	private void disconnectFromRoomInternal() {
		if (DEBUG) Log.v(TAG, "disconnectFromRoomInternal:state=" + roomState);
		cancelCall();
		if ((roomState == ConnectionState.CONNECTED)
			|| (roomState == ConnectionState.JOINED)) {

			if (DEBUG) Log.d(TAG, "Closing room.");
			detach();
		}
		destroy();
	}
	
	private void sendOfferSdpInternal(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendOfferSdpInternal:");
		if (roomState != ConnectionState.CONNECTED) {
			reportError(new RuntimeException("Sending offer SDP in non connected state."));
			return;
		}
		final Call<EventRoom> call = mJanus.offer(
			mSession.id(),
			mPlugin.id(),
			new Message(mRoom,
				new Configure(true, true),
				new JsepSdp("offer", sdp.description))
		);
		addCall(call);
		try {
			final Response<EventRoom> response = call.execute();
			if (DEBUG) Log.v(TAG, "sendOfferSdpInternal:response=" + response
				+ "\n" + response.body());
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				final EventRoom offer = response.body();
				if ("event".equals(offer.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
					final SessionDescription answerSdp
						= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("answer"),
						offer.jsep.sdp);
					events.onRemoteDescription(answerSdp);
				} else if (!"ack".equals(offer.janus)
					&& !"keepalive".equals(offer.janus)) {
					throw new RuntimeException("unexpected response " + response);
				}
				// 実際の待機はlong pollで行う
			} else {
				throw new RuntimeException("failed to send offer sdp");
			}
			if (connectionParameters.loopback) {
				// In loopback mode rename this offer to answer and route it back.
				events.onRemoteDescription(new SessionDescription(
					SessionDescription.Type.fromCanonicalForm("answer"),
					sdp.description));
			}
		} catch (final Exception e) {
			cancelCall();
			reportError(e);
		}
	}
	
	private void sendAnswerSdpInternal(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendAnswerSdpInternal:");
		if (connectionParameters.loopback) {
			Log.e(TAG, "Sending answer in loopback mode.");
			return;
		}
		final Call<ResponseBody> call = mJanus.send(
			mSession.id(),
			mPlugin.id(),
			new Message(mRoom,
				new Start(1234),
				new JsepSdp("answer", sdp.description))
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

	/**
	 * sendLocalIceCandidateの実体、ワーカースレッド上で実行
	 * @param candidate
	 */
	private void sendLocalIceCandidateInternal(final IceCandidate candidate) {
		if (DEBUG) Log.v(TAG, "sendLocalIceCandidateInternal:");
		if (roomState != ConnectionState.CONNECTED) {
			if (DEBUG) Log.d(TAG, "already disconnected");
			return;
		}
		final Call<EventRoom> call;
		if (candidate != null) {
			call = mJanus.trickle(
				mSession.id(),
				mPlugin.id(),
				new Trickle(mRoom, candidate)
			);
		} else {
			call = mJanus.trickleCompleted(
				mSession.id(),
				mPlugin.id(),
				new TrickleCompleted(mRoom)
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
					// FIXME 正常に処理できた…Roomの情報を更新する
					IceCandidate remoteCandidate = null;
					// FIXME removeCandidateを生成する
					if (remoteCandidate != null) {
						events.onRemoteIceCandidate(remoteCandidate);
					} else {
						// FIXME remoteCandidateがなかった時
					}
				} else if (!"ack".equals(join.janus)
					&& !"keepalive".equals(join.janus)) {

					throw new RuntimeException("unexpected response " + response);
				}
				// 実際の待機はlong pollで行う
			} else {
				throw new RuntimeException("unexpected response " + response);
			}
			if ((candidate != null) && (connectionParameters.loopback)) {
				events.onRemoteIceCandidate(candidate);
			}
		} catch (final IOException e) {
			cancelCall();
			detach();
			reportError(e);
		}
	}
	
//--------------------------------------------------------------------
	private void createSession() {
		if (DEBUG) Log.v(TAG, "createSession:");
		// サーバー情報を取得できたらセッションを生成
		final Call<Session> call = mJanus.create(new Creator());
		addCall(call);
		call.enqueue(new Callback<Session>() {
			@Override
			public void onResponse(@NonNull final Call<Session> call,
				@NonNull final Response<Session> response) {

				if (response.isSuccessful() && (response.body() != null)) {
					removeCall(call);
					mSession = response.body();
					if ("success".equals(mSession.janus)) {
						roomState = ConnectionState.READY;
						// セッションを生成できた＼(^o^)／
						if (DEBUG) Log.v(TAG, "createSession#onResponse:" + mSession);
						// VideoRoomプラグインにアタッチ
						handler.post(new Runnable() {
							@Override
							public void run() {
								attach();
							}
						});
					} else {
						mSession = null;
						reportError(new RuntimeException("unexpected response:" + response));
					}
				} else {
					reportError(new RuntimeException("unexpected response:" + response));
				}
			}
			
			@Override
			public void onFailure(@NonNull final Call<Session> call,
				@NonNull final Throwable t) {

				reportError(t);
			}
		});
	}

	/**
	 * attach to VideoRoom plugin
	 */
	private void attach() {
		if (DEBUG) Log.v(TAG, "attach:");
		final Attach attach = new Attach(mSession, "janus.plugin.videoroom");
		final Call<Plugin> call = mJanus.attach(mSession.id(), attach);
		addCall(call);
		call.enqueue(new Callback<Plugin>() {
			@Override
			public void onResponse(@NonNull final Call<Plugin> call,
				@NonNull final Response<Plugin> response) {

				if (response.isSuccessful() && (response.body() != null)) {
					removeCall(call);
					mPlugin = response.body();
					mRoom = new Room(mSession, mPlugin);
					roomState = ConnectionState.ATTACHED;
					// プラグインにアタッチできた＼(^o^)／
					if (DEBUG) Log.v(TAG, "attach#onResponse:" + mRoom);
					// ルームへjoin
					handler.post(new Runnable() {
						@Override
						public void run() {
							join();
						}
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
	
	/**
	 * join Room
	 * @throws IOException
	 */
	private void join() {
		if (DEBUG) Log.v(TAG, "join:");
		final Message message = new Message(mRoom,
			new Join(1234/*FIXME*/, Build.MODEL));
		if (DEBUG) Log.v(TAG, "join:" + message);
		final Call<EventRoom> call = mJanus.join(mSession.id(), mPlugin.id(), message);
		addCall(call);
		try {
			final Response<EventRoom> response = call.execute();
			if (DEBUG) Log.v(TAG, "join:response=" + response + "\n" + response.body());
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				final EventRoom join = response.body();
				longPoll();
				if ("event".equals(join.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
					handleOnJoin(join);
				} else if (!"ack".equals(join.janus)
					&& !"keepalive".equals(join.janus)) {

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
	private void detach() {
		if (DEBUG) Log.v(TAG, "detach:");
		if ((roomState == ConnectionState.CONNECTED)
			|| (roomState == ConnectionState.ATTACHED)
			|| (mPlugin != null)) {

			cancelCall();
			final Call<Void> call = mJanus.detach(mSession.id(), mPlugin.id(), new Detach(mSession));
			addCall(call);
			try {
				call.execute();
			} catch (final IOException e) {
				if (DEBUG) Log.w(TAG, e);
			}
			removeCall(call);
		}
		roomState = ConnectionState.CLOSED;
		mRoom = null;
		mPlugin = null;
	}
	
	/**
	 * destroy session
	 */
	private void destroy() {
		if (DEBUG) Log.v(TAG, "destroy:");
		cancelCall();
		if (mSession != null) {
			final Destroy destroy = new Destroy(mSession);
			final Call<Void> call = mJanus.destroy(mSession.id(), destroy);
			addCall(call);
			try {
				call.execute();
			} catch (final IOException e) {
				reportError(e);
			}
			removeCall(call);
		}
		mRoom = null;
		mPlugin = null;
		mSession = null;
		mServerInfo = null;
		roomState = ConnectionState.CLOSED;
		mJanus = null;
	}

	/**
	 * long poll asynchronously
	 */
	private void longPoll() {
		if (DEBUG) Log.v(TAG, "longPoll:");
		if (mSession == null) return;
		final Call<ResponseBody> call = mLongPoll.getEvent(mSession.id());
		addCall(call);
		call.enqueue(new Callback<ResponseBody>() {
			@Override
			public void onResponse(@NonNull final Call<ResponseBody> call,
				@NonNull final Response<ResponseBody> response) {

				if (DEBUG) Log.v(TAG, "longPoll:onResponse=" + response);
				removeCall(call);
				if ((roomState != ConnectionState.ERROR)
					&& (roomState != ConnectionState.CLOSED)
					&& (roomState != ConnectionState.UNINITIALIZED)
					&& (roomState != ConnectionState.NEW)) {

					try {
						handler.post(new Runnable() {
							@Override
							public void run() {
								handleLongPoll(call, response);
							}
						});
						recall(call);
//						longPoll();
					} catch (final Exception e) {
						reportError(e);
					}
				}
			}
			
			@Override
			public void onFailure(@NonNull final Call<ResponseBody> call, @NonNull final Throwable t) {
				if (DEBUG) Log.v(TAG, "longPoll:onFailure=" + t);
				removeCall(call);
				// FIXME タイムアウトの時は再度long pollする？
				if (!(t instanceof IOException) || !"Canceled".equals(t.getMessage())) {
					reportError(t);
				}
				if (roomState != ConnectionState.ERROR) {
					recall(call);
//					longPoll();
				}
			}
			
			private void recall(final Call<ResponseBody> call) {
				final Call<ResponseBody> newCall = call.clone();
				addCall(newCall);
				newCall.enqueue(this);
			}
		});
	}

	/**
	 * long pollによるjanus-gatewayサーバーからの受信イベントの処理の実体
	 * @param call
	 * @param response
	 */
	private void handleLongPoll(@NonNull final Call<ResponseBody> call,
		@NonNull final Response<ResponseBody> response) {
		
		if (DEBUG) Log.v(TAG, "handleLongPoll:");
		final ResponseBody responseBody = response.body();
		if (response.isSuccessful() && (responseBody != null)) {
			try {
				final JSONObject body = new JSONObject(responseBody.string());
				final String janus = body.optString("janus");
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
						handlePluginEvent(body);
						break;
					case "media":
					case "webrtcup":
					case "slowlink":
					case "hangup":
						// event for WebRTC
						handleWebRTCEvent(body);
						break;
					case "error":
						reportError(new RuntimeException("error response " + response));
						break;
					default:
						Log.d(TAG, "handleLongPoll:unknown event:" + body);
						break;
					}
				}
			} catch (final JSONException | IOException e) {
				reportError(e);
			}
		}
	}

	/**
	 * プラグインイベントの処理
	 * @param body
	 */
	private void handlePluginEvent(final JSONObject body) {
		if (DEBUG) Log.v(TAG, "handlePluginEvent:" + body);
		final Gson gson = new Gson();
		final EventRoom event = gson.fromJson(body.toString(), EventRoom.class);
		final String eventType = (event.plugindata != null) && (event.plugindata.data != null)
			? event.plugindata.data.videoroom : null;
		if (DEBUG) Log.v(TAG, "handlePluginEvent:" + event);
		if (!TextUtils.isEmpty(eventType)) {
			switch (eventType) {
			case "joined":
				handleOnJoin(event);
				break;
			case "event":
				if (event.jsep != null) {
					if ("answer".equals(event.jsep.type)) {
						final SessionDescription answerSdp
							= new SessionDescription(
								SessionDescription.Type.fromCanonicalForm("answer"),
								event.jsep.sdp);
						events.onRemoteDescription(answerSdp);
					} else if ("offer".equals(event.jsep.type)) {
						final SessionDescription offerSdp
							= new SessionDescription(
								SessionDescription.Type.fromCanonicalForm("offer"),
								event.jsep.sdp);
						events.onRemoteDescription(offerSdp);
					}
				}
				if ((event.plugindata != null)
					&& (event.plugindata.data != null)) {

					final EventRoom.Publisher[] publishers = event.plugindata.data.publishers;
					final int n = publishers != null ? publishers.length : 0;
				}
				// FIXME EventRoom#plugindata#data#publishersが変化した時になんかせなあかんのかも
				// FIXME remote candidateの処理がどっかに要る気がするんだけど
//				IceCandidate remoteCandidate = null;
//				// FIXME removeCandidateを生成する
//				if (remoteCandidate != null) {
//					events.onRemoteIceCandidate(remoteCandidate);
//				} else {
//					// FIXME remoteCandidateがなかった時
//				}
				break;
			}
		}
	}
	
	private void handleOnJoin(final EventRoom room) {
		if (DEBUG) Log.v(TAG, "handleOnJoin:");
		// roomにjoinできた
		roomState = ConnectionState.JOINED;
		// FIXME Roomの情報を更新する
		// FIXME ここから先はなにしたらええんやろ？この時点でCONNECTEDでええん？
		roomState = ConnectionState.CONNECTED;
		// 適当にパラメータ作って呼び出してみる
		final SignalingParameters params = new SignalingParameters(
			mCallback.getIceServers(this),
			true, mRoom.clientId(),
			null, null, null, null);
		// Fire connection and signaling parameters events.
		events.onConnectedToRoom(params);
	}

	private void handleWebRTCEvent(final JSONObject body) {
		if (DEBUG) Log.v(TAG, "handleWebRTCEvent:" + body);
		switch (body.optString("janus")) {
		case "media":
		case "webrtcup":
		case "slowlink":
			break;
		case "hangup":
			events.onChannelClose();
			break;
		default:
			break;
		}
	}

//================================================================================
	/**
	 * keep first OkHttpClient as singleton
	 */
	private static OkHttpClient sOkHttpClient;
	/**
	 * Janus-gatewayサーバーとの通信用のOkHttpClientインスタンスの初期化処理
	 * @return
	 */
	private synchronized OkHttpClient setupHttpClient(
		final long readTimeoutMs, final long writeTimeoutMs) {
	
		if (DEBUG) Log.v(TAG, "setupHttpClient:");

		final OkHttpClient.Builder builder;
		if (sOkHttpClient == null) {
		 	builder = new OkHttpClient.Builder();
		} else {
			builder = sOkHttpClient.newBuilder();
		}
		builder
			.addInterceptor(new Interceptor() {
				@Override
				public okhttp3.Response intercept(@NonNull Chain chain) throws IOException {
	
					final Request original = chain.request();
					// header設定
					final Request request = original.newBuilder()
						.header("Accept", "application/json")
						.method(original.method(), original.body())
						.build();
	
					okhttp3.Response response = chain.proceed(request);
					return response;
				}
			})
			.connectTimeout(HTTP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)	// 接続タイムアウト
			.readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)		// 読み込みタイムアウト
			.writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS);	// 書き込みタイムアウト
		
		// ログ出力設定
		if (DEBUG) {
			final HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
			logging.setLevel(HttpLoggingInterceptor.Level.BODY);
			builder.addInterceptor(logging);
		}
		final OkHttpClient result = builder.build();
		if (sOkHttpClient == null) {
			sOkHttpClient = result;
		}
		return result;
	}
	
	/**
	 * Janus-gatewayサーバーとの通信用のRetrofitインスタンスの初期化処理
	 * @param client
	 * @param baseUrl
	 * @return
	 */
	private Retrofit setupRetrofit(@NonNull final OkHttpClient client,
		@NonNull final String baseUrl) {

		if (DEBUG) Log.v(TAG, "setupRetrofit:" + baseUrl);
		// JSONのパーサーとしてGsonを使う
		final Gson gson = new GsonBuilder()
//			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)	// IDENTITY
			.registerTypeAdapter(Date.class, new DateTypeAdapter())
			.create();
		return new Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.client(client)
			.build();
	}

}
