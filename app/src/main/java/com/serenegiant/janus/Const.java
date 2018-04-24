package com.serenegiant.janus;

public class Const {
	/** Janus-gatewayサーバーとの接続タイムアウト設定[ミリ秒] */
	/*package*/ static final long HTTP_CONNECT_TIMEOUT_MS = 3000;
	/** Janus-gatewayサーバーからの読み込みタイムアウト設定[ミリ秒] */
	/*package*/ static final long HTTP_READ_TIMEOUT_MS = 3000;
	/*package*/ static final long HTTP_READ_TIMEOUT_MS_LONG_POLL = 45000;
	/** Janus-gatewayサーバーへの書き込みタイムアウト設定[ミリ秒] */
	/*package*/ static final long HTTP_WRITE_TIMEOUT_MS = 3000;
}
