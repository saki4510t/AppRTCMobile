package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

public class Creator {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	
	public Creator(@NonNull final String transaction) {
		this.janus = "create";
		this.transaction = transaction;
	}
}
