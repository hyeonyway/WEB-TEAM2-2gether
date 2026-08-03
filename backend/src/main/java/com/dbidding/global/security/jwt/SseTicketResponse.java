package com.dbidding.global.security.jwt;

public record SseTicketResponse(
	String ticket,
	long expiresInSeconds
) {
}
