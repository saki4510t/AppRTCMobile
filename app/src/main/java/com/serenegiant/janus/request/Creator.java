package com.serenegiant.janus.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.serenegiant.janus.TransactionManager;

public class Creator {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	
	public Creator(@Nullable final TransactionManager.TransactionCallback callback) {
		this.janus = "create";
		this.transaction = TransactionManager.get(12, callback);
	}
	
	@Override
	public String toString() {
		return "Creator{" +
			"janus='" + janus + '\'' +
			", transaction='" + transaction + '\'' +
			'}';
	}
}
