package com.serenegiant.janus;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.bind.DateTypeAdapter;
import com.serenegiant.janus.request.Creator;
import com.serenegiant.janus.response.ServerInfo;
import com.serenegiant.janus.response.Session;
import com.serenegiant.utils.HandlerThreadHandler;

import org.appspot.apprtc.AppRTCClient;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

	private static enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

	private static class RandomString {
		final String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		final Random rnd = new Random();

		public String get(int length) {
			final StringBuilder sb = new StringBuilder(length);
			for (int i = 0; i < length; i++) {
				sb.append(str.charAt(rnd.nextInt(str.length())));
			}
			return sb.toString();
		}
	}

	private final Object mSync = new Object();
	private final RandomString mRandomString = new RandomString();
	private final WeakReference<Context> mWeakContext;
	private VideoRoom mJanus;
	private Call<?> mCurrentCall;
	private RoomConnectionParameters connectionParameters;
	private Handler handler;
	private ConnectionState roomState;
	private ServerInfo mServerInfo;
	private Session mSession;

	public JanusRESTRTCClient(@NonNull final Context context,
		final SignalingEvents events,
		@NonNull final String baseUrl) {

		mWeakContext = new WeakReference<>(context);
		handler = HandlerThreadHandler.createHandler(TAG);
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
	
	@Override
	public void connectToRoom(final RoomConnectionParameters connectionParameters) {
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
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (DEBUG) Log.v(TAG, "sendOfferSdp#run");
				if (roomState != ConnectionState.CONNECTED) {
					reportError("Sending offer SDP in non connected state.");
					return;
				}
				// FIXME 未実装
//				final JSONObject json = new JSONObject();
//				jsonPut(json, "sdp", sdp.description);
//				jsonPut(json, "type", "offer");
//				sendPostMessage(WebSocketRTCClient.MessageType.MESSAGE, messageUrl, json.toString());
				if (connectionParameters.loopback) {
					// In loopback mode rename this offer to answer and route it back.
					final SessionDescription sdpAnswer = new SessionDescription(
					SessionDescription.Type.fromCanonicalForm("answer"), sdp.description);
//					events.onRemoteDescription(sdpAnswer);
				}
			}
		});
	}
	
	@Override
	public void sendAnswerSdp(final SessionDescription sdp) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (DEBUG) Log.v(TAG, "sendAnswerSdp#run");
				if (connectionParameters.loopback) {
					Log.e(TAG, "Sending answer in loopback mode.");
					return;
				}
				// FIXME 未実装
//				JSONObject json = new JSONObject();
//				jsonPut(json, "sdp", sdp.description);
//				jsonPut(json, "type", "answer");
//				wsClient.send(json.toString());
			}
		});
	}
	
	@Override
	public void sendLocalIceCandidate(final IceCandidate candidate) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (DEBUG) Log.v(TAG, "sendLocalIceCandidate#run");
				// FIXME 未実装
//				final JSONObject json = new JSONObject();
//				jsonPut(json, "type", "candidate");
//				jsonPut(json, "label", candidate.sdpMLineIndex);
//				jsonPut(json, "id", candidate.sdpMid);
//				jsonPut(json, "candidate", candidate.sdp);
//				if (initiator) {
//					// Call initiator sends ice candidates to GAE server.
//					if (roomState != WebSocketRTCClient.ConnectionState.CONNECTED) {
//						reportError("Sending ICE candidate in non connected state.");
//						return;
//					}
//					sendPostMessage(WebSocketRTCClient.MessageType.MESSAGE, messageUrl, json.toString());
//					if (connectionParameters.loopback) {
//						events.onRemoteIceCandidate(candidate);
//					}
//				} else {
//					// Call receiver sends ice candidates to websocket server.
//					wsClient.send(json.toString());
//				}
			}
		});
	}
	
	@Override
	public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (DEBUG) Log.v(TAG, "sendLocalIceCandidateRemovals#run");
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
	private void setCall(final Call<?> call) {
		synchronized (mSync) {
			mCurrentCall = call;
		}
	}
	
	/**
	 * cancel call if call is in progress
	 */
	private void cancelCall() {
		synchronized (mSync) {
			if ((mCurrentCall != null) && !mCurrentCall.isCanceled()) {
				try {
					mCurrentCall.cancel();
				} catch (final Exception e) {
					mCurrentCall = null;
					Log.w(TAG, e);
				}
			}
		}
	}

	private void initAsync(@NonNull final String baseUrl) {
		if (DEBUG) Log.v(TAG, "initAsync:" + baseUrl);
		handler.post(new Runnable() {
			@Override
			public void run() {
				mJanus = setupRetrofit(setupHttpClient(), baseUrl).create(VideoRoom.class);
				final Call<ServerInfo> call = mJanus.getInfo();
				setCall(call);
				call.enqueue(new Callback<ServerInfo>() {
					@Override
					public void onResponse(@NonNull final Call<ServerInfo> call, @NonNull final Response<ServerInfo> response) {
						if (DEBUG) Log.v(TAG, "initAsync#onResponse:" + response);
						setCall(null);
						if (response.isSuccessful() && (response.body() != null)) {
							mServerInfo = response.body();
							if (DEBUG) Log.v(TAG, "initAsync#onResponse:" + mServerInfo);
							createSession();
						}
					}
					
					@Override
					public void onFailure(@NonNull final Call<ServerInfo> call, @NonNull final Throwable t) {
						if (DEBUG) Log.v(TAG, "initAsync#onFailure" + t);
						setCall(null);
						reportError(t.getMessage());
					}
				});
			}
		});
	}
	
	private void createSession() {
		if (DEBUG) Log.v(TAG, "createSession:");
		handler.post(new Runnable() {
			@Override
			public void run() {
				final Call<Session> call = mJanus.create(new Creator(mRandomString.get(12)));
				setCall(call);
				call.enqueue(new retrofit2.Callback<Session>() {
					@Override
					public void onResponse(@NonNull final Call<Session> call,
						@NonNull final Response<Session> response) {
					
						if (DEBUG) Log.v(TAG, "createSession#onResponse:" + response);
						setCall(null);
						if (response.isSuccessful() && (response.body() != null)) {
							mSession = response.body();
							if (DEBUG) Log.v(TAG, "createSession#onResponse:" + mSession);
							// FIXME 未実装
						}
					}
					
					@Override
					public void onFailure(@NonNull final Call<Session> call,
						@NonNull final Throwable t) {
					
						if (DEBUG) Log.v(TAG, "createSession#onFailure" + t);
						setCall(null);
						reportError(t.getMessage());
					}
				});
			}
		});
	}
	
	// Connects to room - function runs on a local looper thread.
	private void connectToRoomInternal() {
		if (DEBUG) Log.v(TAG, "connectToRoomInternal:");
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

	// Disconnect from room and send bye messages - runs on a local looper thread.
	private void disconnectFromRoomInternal() {
		if (DEBUG) Log.v(TAG, "disconnectFromRoomInternal:state=" + roomState);
//		if (roomState == ConnectionState.CONNECTED) {
//			if (DEBUG) Log.d(TAG, "Closing room.");
//			sendPostMessage(WebSocketRTCClient.MessageType.LEAVE, leaveUrl, null);
//		}
//		roomState = WebSocketRTCClient.ConnectionState.CLOSED;
//		if (wsClient != null) {
//			wsClient.disconnect(true);
//		}
	}
	
//--------------------------------------------------------------------
	private void reportError(final String errorMessage) {
		Log.e(TAG, errorMessage);
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (roomState != ConnectionState.ERROR) {
					roomState = ConnectionState.ERROR;
//					events.onChannelError(errorMessage);	// FIXME
				}
			}
		});
	}
	
	/**
	 * Janus-gatewayサーバーとの通信用のOkHttpClientインスタンスの初期化処理
	 * @return
	 */
	private OkHttpClient setupHttpClient() {
		if (DEBUG) Log.v(TAG, "setupHttpClient:");

		final OkHttpClient.Builder builder = new OkHttpClient.Builder();
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
			.readTimeout(HTTP_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)		// 読み込みタイムアウト
			.writeTimeout(HTTP_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);	// 書き込みタイムアウト
		
		// ログ出力設定
		if (DEBUG) {
			final HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
			logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
			builder.addInterceptor(logging);
		}
		return builder.build();
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
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.registerTypeAdapter(Date.class, new DateTypeAdapter())
			.create();
		return new Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.client(client)
			.build();
	}

}
