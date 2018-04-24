package com.serenegiant.janus;

import org.appspot.apprtc.AppRTCClient;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class JanusRESTRTCClient implements AppRTCClient {
	@Override
	public void connectToRoom(final RoomConnectionParameters connectionParameters) {
	
	}
	
	@Override
	public void sendOfferSdp(final SessionDescription sdp) {
	
	}
	
	@Override
	public void sendAnswerSdp(final SessionDescription sdp) {
	
	}
	
	@Override
	public void sendLocalIceCandidate(final IceCandidate candidate) {
	
	}
	
	@Override
	public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
	
	}
	
	@Override
	public void disconnectFromRoom() {
	
	}
}
