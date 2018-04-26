package com.serenegiant.janus;

import android.content.Context;
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

	private static enum ConnectionState {
		NEW,
		ATTACHED,
		JOINED,
		CONNECTED,
		CLOSED,
		ERROR }


	private final Object mSync = new Object();
	private final WeakReference<Context> mWeakContext;
	private final SignalingEvents events;
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
		final SignalingEvents events,
		@NonNull final String baseUrl) {

		this.mWeakContext = new WeakReference<>(context);
		this.events = events;
		this.handler = HandlerThreadHandler.createHandler(TAG);
		this.roomState = ConnectionState.NEW;
		initAsync(baseUrl);
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
		handler.post(new Runnable() {
			@Override
			public void run() {
				sendLocalIceCandidateInternal(candidate);
			}
		});
	}
	
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
	 * 初期化処理
	 * @param baseUrl
	 */
	private void initAsync(@NonNull final String baseUrl) {
		if (DEBUG) Log.v(TAG, "initAsync:" + baseUrl);
		handler.post(new Runnable() {
			@Override
			public void run() {
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
				try {
					final Response<ServerInfo> response = call.execute();
					if (DEBUG) Log.v(TAG, "initAsync#onResponse:" + response);
					removeCall(call);
					if (response.isSuccessful() && (response.body() != null)) {
						mServerInfo = response.body();
						if (DEBUG) Log.v(TAG, "initAsync#onResponse:" + mServerInfo);
					} else {
						reportError(new RuntimeException("initAsync: unexpected response " + response));
					}
				} catch (final IOException e) {
					removeCall(call);
					reportError(e);
				}
				if (mServerInfo != null) {
					// サーバー情報を取得できたらセッションを生成
					final Call<Session> createCall = mJanus.create(new Creator());
					addCall(call);
					try {
						final Response<Session> response = createCall.execute();
						if (DEBUG) Log.v(TAG, "initAsync#onResponse:" + response);
						removeCall(call);
						if (response.isSuccessful() && (response.body() != null)) {
							// セッションを生成できた＼(^o^)／
							mSession = response.body();
							if (DEBUG) Log.v(TAG, "initAsync#onResponse:" + mSession);
						}
					} catch (final IOException e) {
						removeCall(call);
						reportError(e);
					}
				}
			}
		});
	}
	
	/**
	 * Connects to room - function runs on a local looper thread.
	 */
	private void connectToRoomInternal() {
		if (DEBUG) Log.v(TAG, "connectToRoomInternal:");
		if (mSession != null) {
			roomState = ConnectionState.NEW;
			// VideoRoomプラグインにアタッチ
			attach();
			if (roomState == ConnectionState.ATTACHED) {
				// VideoRoomプラグインにアタッチできた
				join();
			}
			if (roomState == ConnectionState.JOINED) {
				// roomにjoinできた
				connect();
			}
		} else {
			reportError(new RuntimeException("session is not ready/already disconnected"));
		}
//		String connectionUrl = getConnectionUrl(connectionParameters);
//		Log.d(TAG, "Connect to room: " + connectionUrl);
//		roomState = ConnectionState.NEW;
//		wsClient = new WebSocketChannelClient(handler, this);
//
//		RoomParametersFetcher.RoomParametersFetcherEvents callbacks
//			= new RoomParametersFetcher.RoomParametersFetcherEvents() {
//
//			@Override
//			public void onSignalingParametersReady(final SignalingParameters params) {
//				handler.post(new Runnable() {
//					@Override
//					public void run() {
//						signalingParametersReady(params);
//					}
//				});
//			}
//
//			@Override
//			public void onSignalingParametersError(String description) {
//				reportError(description);
//			}
//		};
//
//		new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
	}

	/**
	 * Disconnect from room and send bye messages - runs on a local looper thread.
	 */
	private void disconnectFromRoomInternal() {
		if (DEBUG) Log.v(TAG, "disconnectFromRoomInternal:state=" + roomState);
		cancelCall();
		detach();
		if (roomState == ConnectionState.CONNECTED) {
			if (DEBUG) Log.d(TAG, "Closing room.");
//			sendPostMessage(WebSocketRTCClient.MessageType.LEAVE, leaveUrl, null);
		}
		destroy();
//		if (wsClient != null) {
//			wsClient.disconnect(true);
//		}
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
//			if (DEBUG) Log.v(TAG, "sendOfferSdpInternal:response=" + response
//				+ "\n" + response.body());
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
	
//	private void waitAnswer(final SessionDescription sdp) {
//		final Call<EventRoom> waitCall = mLongPoll.getRoomEvent(mSession.id());
//		waitCall.enqueue(new Callback<EventRoom>() {
//			@Override
//			public void onResponse(@NonNull final Call<EventRoom> call, @NonNull final Response<EventRoom> response) {
//				if (response.isSuccessful() && (response.body() != null)) {
//					final EventRoom event = response.body();
//					if (DEBUG) Log.v(TAG, "waitRemoteIceCandidate:EventRoom received " + event);
//					if (!TextUtils.isEmpty(event.janus)) {
//						switch (event.janus) {
//						case "ack":
////							enqueue(waitCall.clone());
//							break;
//						case "keepalive":
////							enqueue(waitCall.clone());
//							break;
//						case "event":
//							removeCall(call);
//							// FIXME 正常に処理できた…Roomの情報を更新する
//							try {
//								final SessionDescription answerSdp
//									= new SessionDescription(
//										SessionDescription.Type.fromCanonicalForm("answer"),
//										event.jsep.sdp);
//								events.onRemoteDescription(answerSdp);	// FIXME サーバー側がおかしい？
//							} catch (final Exception e) {
//								reportError(e);
//							}
//							break;
//						default:
//							reportError(new RuntimeException("unexpected response " + response));
//						}
//					} else {
//						reportError(new RuntimeException("unexpected response " + response));
//					}
//				} else if (roomState == ConnectionState.CONNECTED) {
//					Log.w(TAG, "なんかわからんけど期待したのと違うレスポオンスが返ってきた" + response);
////					enqueue(waitCall.clone());
//				} else {
//					reportError(new RuntimeException("unexpected state " + response));
//				}
//			}
//
//			@Override
//			public void onFailure(@NonNull final Call<EventRoom> call, @NonNull final Throwable t) {
//				reportError(t);
//			}
//
//			private void enqueue(final Call<EventRoom> call) {
//				addCall(call);
//				call.enqueue(this);
//			}
//		});
//	}

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
		final Call<EventRoom> call = mJanus.trickle(
			mSession.id(),
			mPlugin.id(),
			new Trickle(mRoom, candidate)
		);
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
			} else {
				throw new RuntimeException("unexpected response " + response);
			}
			if (connectionParameters.loopback) {
				events.onRemoteIceCandidate(candidate);
			}
		} catch (final IOException e) {
			cancelCall();
			detach();
			reportError(e);
		}
	}
	
//	/**
//	 * sendLocalIceCandidateInternalの下請け
//	 */
//	private void waitRemoteIceCandidate(final IceCandidate candidate) {
//		final Call<EventRoom> waitCall = mLongPoll.getRoomEvent(mSession.id());
//		waitCall.enqueue(new Callback<EventRoom>() {
//			@Override
//			public void onResponse(@NonNull final Call<EventRoom> call, @NonNull final Response<EventRoom> response) {
//				if (response.isSuccessful() && (response.body() != null)) {
//					final EventRoom event = response.body();
//					if (DEBUG) Log.v(TAG, "waitRemoteIceCandidate:EventRoom received " + event);
//					if (!TextUtils.isEmpty(event.janus)) {
//						switch (event.janus) {
//						case "ack":
////							enqueue(waitCall.clone());
//							break;
//						case "keepalive":
////							enqueue(waitCall.clone());
//							break;
//						case "event":
//							removeCall(call);
//							// FIXME 正常に処理できた…Roomの情報を更新する
//							IceCandidate remoteCandidate = null;
//							// FIXME removeCandidateを生成する
//							if (remoteCandidate != null) {
//								events.onRemoteIceCandidate(remoteCandidate);
//							} else {
//								// FIXME remoteCandidateがなかった時, リトライする？
//							}
//							break;
//						case "media":
//							// FIXME ここでなんかせなあかん
//							break;
//						case "webrtcup":
//							break;
//						default:
//							reportError(new RuntimeException("unexpected response " + response));
//						}
//					} else {
//						reportError(new RuntimeException("unexpected response " + response));
//					}
//				} else if (roomState == ConnectionState.CONNECTED) {
//					Log.w(TAG, "なんかわからんけど期待したのと違うレスポオンスが返ってきた" + response);
////					enqueue(waitCall.clone());
//				} else {
//					reportError(new RuntimeException("unexpected state " + response));
//				}
//			}
//
//			@Override
//			public void onFailure(@NonNull final Call<EventRoom> call, @NonNull final Throwable t) {
//				reportError(t);
//			}
//
//			private void enqueue(final Call<EventRoom> call) {
//				addCall(call);
//				call.enqueue(this);
//			}
//		});
//	}

//--------------------------------------------------------------------
	private void handleLongPoll(@NonNull final Call<ResponseBody> call,
		@NonNull final Response<ResponseBody> response) {
		
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
						// do nothing
						break;
					case "event":
						handleEvent(body);
						break;
					case "media":
						break;
					}
				}
			} catch (final JSONException | IOException e) {
				reportError(e);
			}
		}
	}

	private void handleEvent(final JSONObject body) {
		if (DEBUG) Log.v(TAG, "handleEvent:" + body);
		final Gson gson = new Gson();
		final EventRoom event = gson.fromJson(body.toString(), EventRoom.class);
		if (event.jsep != null) {
			if ("answer".equals(event.jsep.type)) {
				final SessionDescription answerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("answer"),
						event.jsep.sdp);
				events.onRemoteDescription(answerSdp);
			} else if ("offer".equals(event.jsep.type)) {
				final SessionDescription answerSdp
					= new SessionDescription(
						SessionDescription.Type.fromCanonicalForm("offer"),
						event.jsep.sdp);
				events.onRemoteDescription(answerSdp);
			}
		}
	}

	private void handleMedia(final JSONObject body) {
		if (DEBUG) Log.v(TAG, "handleMedia:" + body);
		// FIXME 未実装
	}

	/**
	 * long poll asynchronously
	 */
	private void longPoll() {
		if (DEBUG) Log.v(TAG, "longPoll:");
		final Call<ResponseBody> call = mLongPoll.getEvent(mSession.id());
		addCall(call);
		call.enqueue(new Callback<ResponseBody>() {
			@Override
			public void onResponse(@NonNull final Call<ResponseBody> call,
				@NonNull final Response<ResponseBody> response) {

//				removeCall(call);
//				longPoll();
				handleLongPoll(call, response);
				// サーバー側がタイムアウト(30秒？)した時は{"janus": "keepalive"}が来る
				if (DEBUG) {
					try {
						Log.v(TAG, "longPoll:onResponse=" + response
							+ "\nevent=" + response.body().string());
					} catch (final IOException e) {
						Log.w(TAG, e);
					}
				}
			}
			
			@Override
			public void onFailure(@NonNull final Call<ResponseBody> call, @NonNull final Throwable t) {
				if (DEBUG) Log.v(TAG, "longPoll:onFailure=" + t);
//				removeCall(call);
				// FIXME タイムアウトの時は再度long pollする？
				if (!(t instanceof IOException) || !"Canceled".equals(t.getMessage())) {
					reportError(t);
				}
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
		try {
			final Response<Plugin> response = call.execute();
			if (DEBUG) Log.v(TAG, "attach#onResponse:" + response);
			removeCall(call);
			if (response.isSuccessful() && (response.body() != null)) {
				mPlugin = response.body();
				mRoom = new Room(mSession, mPlugin);
				roomState = ConnectionState.ATTACHED;
				if (DEBUG) Log.v(TAG, "attach#onResponse:" + mPlugin);
			} else {
				reportError(new RuntimeException("attach:unexpected response " + response));
			}
		} catch (final IOException e) {
			cancelCall();
			reportError(e);
		}
	}
	
	/**
	 * join Room
	 * @throws IOException
	 */
	private void join() {
		if (DEBUG) Log.v(TAG, "join:");
		final Message message = new Message(mRoom,
			new Join(1234/*FIXME*/, "android"));
		if (DEBUG) Log.v(TAG, "join:" + message);
		final Call<EventRoom> call = mJanus.join(mSession.id(), mPlugin.id(), message);
		addCall(call);
		try {
			Response<EventRoom> response = call.execute();
			if (DEBUG) Log.v(TAG, "join:response=" + response + "\n" + response.body());
			if (response.isSuccessful() && (response.body() != null)) {
				removeCall(call);
				final EventRoom join = response.body();
				if ("event".equals(join.janus)) {
					if (DEBUG) Log.v(TAG, "多分ここにはこない, ackが返ってくるはず");
					// FIXME Roomの情報を更新する
				} else if ("ack".equals(join.janus)
					|| "keepalive".equals(join.janus)) {

					// FIXME これは非同期で呼び出したほうがいいような気がする
					final Call<EventRoom> waitCall = mLongPoll.getRoomEvent(mSession.id());
LOOP:				for ( ; ; ) {
						final Call<EventRoom> c = waitCall.clone();
						addCall(c);
						response = c.execute();
						if (response.isSuccessful() && (response.body() != null)) {
							removeCall(c);
							final EventRoom event = response.body();
							if (!TextUtils.isEmpty(event.janus)) {
								switch (event.janus) {
								case "ack":
									continue;
								case "keepalive":
									continue;
								case "event":
									// FIXME Roomの情報を更新する
									break LOOP;
								default:
									throw new RuntimeException("unexpected response " + response);
								}
							}
						} else {
							throw new RuntimeException("unexpected response " + response);
						}
					}
				} else {
					throw new RuntimeException("unexpected response " + response);
				}
			} else {
				throw new RuntimeException("unexpected response " + response);
			}
			roomState = ConnectionState.JOINED;
		} catch (final Exception e) {
			cancelCall();
			detach();
			reportError(e);
		}
	}
	
	private void connect() {
		// FIXME ここから先はなにしたらええんやろ？この時点でCONNECTEDでええん？
		roomState = ConnectionState.CONNECTED;
		// long pollによるイベント発生待ちを開始
		longPoll();
		// 適当にパラメータ作って呼び出してみる
		final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
		final SignalingParameters params = new SignalingParameters(
			iceServers, true, mRoom.clientId(),
				null, null, null, null);
		// Fire connection and signaling parameters events.
		events.onConnectedToRoom(params);
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
