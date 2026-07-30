package com.dbidding.global.security;

public record SseTicketResponse(
	String ticket,
	long expiresInSeconds
) {
}
