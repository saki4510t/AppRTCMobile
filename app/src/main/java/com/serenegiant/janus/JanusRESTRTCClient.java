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
import com.serenegiant.janus.request.Creator;
import com.serenegiant.janus.request.Destroy;
import com.serenegiant.janus.response.EventRoom;
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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
	private final Map<BigInteger, JanusPlugin> mAttachedPlugins
		= new ConcurrentHashMap<BigInteger, JanusPlugin>();
	private RoomConnectionParameters connectionParameters;
	private Handler handler;
	private ConnectionState mConnectionState;
	private ServerInfo mServerInfo;
	private Session mSession;

	public JanusRESTRTCClient(@NonNull final Context context,
		@NonNull final SignalingEvents events,
		@NonNull final JanusCallback callback,
		@NonNull final String baseUrl) {

		this.mWeakContext = new WeakReference<>(context);
		this.events = events;
		this.mCallback = callback;
		this.baseUrl = baseUrl;
		this.handler = HandlerThreadHandler.createHandler(TAG);
		this.mConnectionState = ConnectionState.UNINITIALIZED;
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
		handler.post(() -> {
			connectToRoomInternal();
		});
	}
	
	@Override
	public void sendOfferSdp(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendOfferSdp:" + sdp);
		handler.post(() -> {
			sendOfferSdpInternal(sdp);
		});
	}
	
	@Override
	public void sendAnswerSdp(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendAnswerSdp:" + sdp);
		handler.post(() -> {
			sendAnswerSdpInternal(sdp);
		});
	}
	
	@Override
	public void sendLocalIceCandidate(final IceCandidate candidate) {
		if (DEBUG) Log.v(TAG, "sendLocalIceCandidate:");
		if (candidate != null) {
			handler.removeCallbacks(mTrySendTrickleCompletedTask);
			handler.post(() -> {
				sendLocalIceCandidateInternal(candidate);
			});
			handler.postDelayed(mTrySendTrickleCompletedTask, 50);
		} else {
			handler.post(mTrySendTrickleCompletedTask);
		}
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
		handler.post(() -> {
			// FIXME 未実装
//			final JSONObject json = new JSONObject();
//			jsonPut(json, "type", "remove-candidates");
//			final JSONArray jsonArray = new JSONArray();
//			for (final IceCandidate candidate : candidates) {
//				jsonArray.put(toJsonCandidate(candidate));
//			}
//			jsonPut(json, "candidates", jsonArray);
//			if (initiator) {
//				// Call initiator sends ice candidates to GAE server.
//				if (mConnectionState != WebSocketRTCClient.ConnectionState.CONNECTED) {
//					reportError("Sending ICE candidate removals in non connected state.");
//					return;
//				}
//				sendPostMessage(WebSocketRTCClient.MessageType.MESSAGE, messageUrl, json.toString());
//				if (connectionParameters.loopback) {
//					events.onRemoteIceCandidatesRemoved(candidates);
//				}
//			} else {
//				// Call receiver sends ice candidates to websocket server.
//				wsClient.send(json.toString());
//			}
		});
	}
	
	@Override
	public void disconnectFromRoom() {
		if (DEBUG) Log.v(TAG, "disconnectFromRoom:");
		cancelCall();
		handler.post(() -> {
			disconnectFromRoomInternal();
			try {
				handler.getLooper().quit();
			} catch (final Exception e) {
				if (DEBUG) Log.w(TAG, e);
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

//--------------------------------------------------------------------------------
	private void addPlugin(@NonNull final BigInteger key, @NonNull final JanusPlugin plugin) {
		synchronized (mAttachedPlugins) {
			mAttachedPlugins.put(key, plugin);
		}
	}

	private void removePlugin(@NonNull final JanusPlugin plugin) {
		final BigInteger key = plugin instanceof JanusPlugin.Publisher
			? BigInteger.ZERO
			: ((JanusPlugin.Subscriber)plugin).feederId;
		
		handler.post(() -> {
			synchronized (mAttachedPlugins) {
				mAttachedPlugins.remove(key);
			}
		});
	}

	private void removePlugin(@NonNull final BigInteger key) {
		handler.post(() -> {
			synchronized (mAttachedPlugins) {
				mAttachedPlugins.remove(key);
			}
		});
	}

	private JanusPlugin getPlugin(@Nullable final BigInteger key) {
		synchronized (mAttachedPlugins) {
			if ((key != null) && mAttachedPlugins.containsKey(key)) {
				return mAttachedPlugins.get(key);
			}
		}
		return null;
	}
//--------------------------------------------------------------------------------
	/**
	 * notify error
	 * @param t
	 */
	private void reportError(@NonNull final Throwable t) {
		Log.w(TAG, t);
		cancelCall();
		TransactionManager.clearTransactions();
		try {
			handler.post(() -> {
				if (mConnectionState != ConnectionState.ERROR) {
					mConnectionState = ConnectionState.ERROR;
					events.onChannelError(t.getMessage());
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
		handler.post(() -> {
			requestServerInfo();
		});
	}

	/**
	 * Disconnect from room and send bye messages - runs on a local looper thread.
	 */
	private void disconnectFromRoomInternal() {
		if (DEBUG) Log.v(TAG, "disconnectFromRoomInternal:state=" + mConnectionState);
		cancelCall();
		if ((mConnectionState == ConnectionState.CONNECTED)) {

			if (DEBUG) Log.d(TAG, "Closing room.");
			detach();
		}
		destroy();
	}
	
	private void sendOfferSdpInternal(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendOfferSdpInternal:");
		if (mConnectionState != ConnectionState.CONNECTED) {
			reportError(new RuntimeException("Sending offer SDP in non connected state."));
			return;
		}
		final JanusPlugin plugin;
		synchronized (mAttachedPlugins) {
			plugin = mAttachedPlugins.containsKey(BigInteger.ZERO)
				? mAttachedPlugins.get(BigInteger.ZERO) : null;
		}
		if (plugin != null) {
			plugin.sendOfferSdp(sdp, connectionParameters.loopback);
		} else {
			reportError(new IllegalStateException("publisher not found"));
		}
	}
	
	private void sendAnswerSdpInternal(final SessionDescription sdp) {
		if (DEBUG) Log.v(TAG, "sendAnswerSdpInternal:");
		if (connectionParameters.loopback) {
			Log.e(TAG, "Sending answer in loopback mode.");
			return;
		}
		final JanusPlugin plugin;
		synchronized (mAttachedPlugins) {
			plugin = mAttachedPlugins.containsKey(BigInteger.ZERO)
				? mAttachedPlugins.get(BigInteger.ZERO) : null;
		}
		if (plugin != null) {
			plugin.sendAnswerSdp(sdp, connectionParameters.loopback);
		} else {
			reportError(new IllegalStateException("publisher not found"));
		}
	}

	/**
	 * sendLocalIceCandidateの実体、ワーカースレッド上で実行
	 * @param candidate
	 */
	private void sendLocalIceCandidateInternal(final IceCandidate candidate) {
		if (DEBUG) Log.v(TAG, "sendLocalIceCandidateInternal:");
		if (mConnectionState != ConnectionState.CONNECTED) {
			if (DEBUG) Log.d(TAG, "already disconnected");
			return;
		}
		final JanusPlugin plugin;
		synchronized (mAttachedPlugins) {
			plugin = mAttachedPlugins.containsKey(BigInteger.ZERO)
				? mAttachedPlugins.get(BigInteger.ZERO) : null;
		}
		if (plugin != null) {
			plugin.sendLocalIceCandidate(candidate,
				connectionParameters.loopback);
		} else {
			reportError(new IllegalStateException("there is no publisher"));
		}
	}
	
//--------------------------------------------------------------------
	private void requestServerInfo() {
		if (DEBUG) Log.v(TAG, "requestServerInfo:");
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
					if (DEBUG) Log.v(TAG, "requestServerInfo:success");
					handler.post(() -> {
						createSession();
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
						mConnectionState = ConnectionState.READY;
						// セッションを生成できた＼(^o^)／
						if (DEBUG) Log.v(TAG, "createSession:success");
						// パブリッシャーをVideoRoomプラグインにアタッチ
						handler.post(() -> {
							longPoll();
							final JanusPlugin.Publisher publisher
								= new JanusPlugin.Publisher(
									mJanus, mSession, mJanusPluginCallback);
							// ローカルのPublisherは1つしかないので検索の手間を省くために
							// BigInteger.ZEROをキーとして登録しておく。
							// attachした時点で実際のプラグインのidでも登録される
							addPlugin(BigInteger.ZERO, publisher);
							publisher.attach();
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
	 * detach from VideoRoom plugin
	 */
	private void detach() {
		if (DEBUG) Log.v(TAG, "detach:");
		cancelCall();
		mConnectionState = ConnectionState.CLOSED;
		synchronized (mAttachedPlugins) {
			for (final Map.Entry<BigInteger, JanusPlugin> entry:
				mAttachedPlugins.entrySet()) {

				// key === BigInteger.ZEROはPublisherのキーの別名で、
				// 本当のidでも別途登録されているはずなのでここでは呼ばない
				if (!BigInteger.ZERO.equals(entry.getKey())) {
					entry.getValue().detach();
				}
			}
			mAttachedPlugins.clear();
		}
	}
	
	/**
	 * destroy session
	 */
	private void destroy() {
		if (DEBUG) Log.v(TAG, "destroy:");
		cancelCall();
		detach();
		if (mSession != null) {
			final Destroy destroy = new Destroy(mSession, null);
			final Call<Void> call = mJanus.destroy(mSession.id(), destroy);
			addCall(call);
			try {
				call.execute();
			} catch (final IOException e) {
				reportError(e);
			}
			removeCall(call);
		}
		mSession = null;
		mServerInfo = null;
		mConnectionState = ConnectionState.CLOSED;
		TransactionManager.clearTransactions();
		mJanus = null;
	}

	/**
	 * JanusPluginからのコールバックリスナーの実装
	 */
	private final JanusPlugin.JanusPluginCallback mJanusPluginCallback
		= new JanusPlugin.JanusPluginCallback() {
		@Override
		public void onAttach(@NonNull final JanusPlugin plugin) {
			if (DEBUG) Log.v(TAG, "onAttach:" + plugin);
			addPlugin(plugin.id(), plugin);
		}
		
		@Override
		public void onJoin(@NonNull final JanusPlugin plugin,
			final EventRoom room) {

			if (DEBUG) Log.v(TAG, "onJoin:" + plugin);
			if (plugin instanceof JanusPlugin.Publisher) {
				mConnectionState = ConnectionState.CONNECTED;
				handleOnJoin(room);	// FIXME publisherの時はhandleJoin呼んじゃダメかも
			} else if (plugin instanceof JanusPlugin.Subscriber) {
//				mConnectionState = ConnectionState.CONNECTED;
//				handleOnJoin(room);
			}
		}
		
		@Override
		public void onDetach(@NonNull final JanusPlugin plugin) {
			if (DEBUG) Log.v(TAG, "onDetach:" + plugin);
			removePlugin(plugin);
		}
		
		@Override
		public void onLeave(@NonNull final JanusPlugin plugin,
			@NonNull final BigInteger pluginId) {
			
			if (DEBUG) Log.v(TAG, "onLeave:" + plugin + ",leave=" + pluginId);
		}
		
		@Override
		public void onRemoteIceCandidate(@NonNull final JanusPlugin plugin,
			final IceCandidate candidate) {

			if (DEBUG) Log.v(TAG, "onRemoteIceCandidate:" + plugin
				+ "\n" + candidate);
		}
		
		@Override
		public void onRemoteDescription(@NonNull final JanusPlugin plugin,
			final SessionDescription sdp) {
			
			if (DEBUG) Log.v(TAG, "onRemoteDescription:" + plugin
				+ "\n" + sdp);
			if (plugin instanceof JanusPlugin.Publisher) {
				events.onRemoteDescription(sdp);
			} else if (plugin instanceof JanusPlugin.Subscriber) {
				if (sdp.type == SessionDescription.Type.ANSWER) {
					events.onRemoteDescription(sdp);
				} else {
					final SessionDescription answerSdp
						= new SessionDescription(SessionDescription.Type.ANSWER,
							sdp.description);
					events.onRemoteDescription(sdp);
				}
			}
		}
		
		@Override
		public void onError(@NonNull final JanusPlugin plugin,
			@NonNull final Throwable t) {

			reportError(t);
		}
	};

	/**
	 * TransactionManagerからのコールバック
	 */
	private final TransactionManager.TransactionCallback mTransactionCallback
		= new TransactionManager.TransactionCallback() {
		@Override
		public boolean onReceived(@NonNull final String transaction,
			final JSONObject json) {

			if (DEBUG) Log.v(TAG, "onReceived:" + json);
			return false;
		}
	};

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

				if (DEBUG) Log.v(TAG, "longPoll:onResponse");
				removeCall(call);
				if ((mConnectionState != ConnectionState.ERROR)
					&& (mConnectionState != ConnectionState.CLOSED)
					&& (mConnectionState != ConnectionState.UNINITIALIZED)) {

					try {
						handler.post(() -> {
							handleLongPoll(call, response);
						});
						recall(call);
					} catch (final Exception e) {
						reportError(e);
					}
				} else {
					Log.w(TAG, "unexpected state:" + mConnectionState);
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
				if (mConnectionState != ConnectionState.ERROR) {
					recall(call);
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
				final String transaction = body.optString("transaction");
				if (!TextUtils.isEmpty(transaction)) {
					// トランザクションコールバックでの処理を試みる
					// WebRTCイベントはトランザクションがない
					if (TransactionManager.handleTransaction(transaction, body)) {
						return;	// 処理済みの時はここで終了
					}
				}

				if (DEBUG) Log.v(TAG, "handleLongPoll:unhandled transaction");
				final String janus = body.optString("janus");
				if (!TextUtils.isEmpty(janus)) {
					switch (janus) {
					case "ack":
						// do nothing
						return;
					case "keepalive":
						// サーバー側がタイムアウト(30秒？)した時は{"janus": "keepalive"}が来る
						// do nothing
						return;
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

		// Senderフィールドは対象のプラグインのidなので対応するプラグインを探して実行を試みる
		final JanusPlugin plugin = getPlugin(event.sender);
		if (plugin != null) {
			if (DEBUG) Log.v(TAG, "handlePluginEvent: try handle message on plugin specified by sender");
			if (plugin.onReceived("", body)) return;
		}
		
		if (DEBUG) Log.v(TAG, "handlePluginEvent: unhandled event");
	}
	
	private void handleOnJoin(final EventRoom room) {
		if (DEBUG) Log.v(TAG, "handleOnJoin:");
		// roomにjoinできた
		final SignalingParameters params = new SignalingParameters(
			mCallback.getIceServers(this),
				true,						// initiator=trueならこの端末側がofferする
				room.plugindata.data.id.toString(),	// clientId
				null, null,
				null, null);	// この2つはinitiator=falseの時有効
		// Fire connection and signaling parameters events.
		events.onConnectedToRoom(params);
	}

	/**
	 * WebRTC関係のメッセージの処理
	 * @param body
	 */
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
