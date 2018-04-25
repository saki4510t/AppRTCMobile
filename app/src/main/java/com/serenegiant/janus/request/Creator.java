package com.serenegiant.janus.request;

import android.support.annotation.NonNull;

public class Creator {
	@NonNull
	public final String janus;
	@NonNull
	public final String transaction;
	
	public Creator() {
		this.janus = "create";
		this.transaction = TransactionGenerator.get(12);
	}
}
