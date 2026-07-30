package com.dbidding.global.security;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sse/tickets")
@RequiredArgsConstructor
public class SseTicketController {

	private static final long TICKET_TTL_SECONDS = 30L;

	private final TicketProvider ticketProvider;

	@PostMapping
	public SseTicketResponse issue(@CurrentUser Integer userId) {
		return new SseTicketResponse(ticketProvider.issue(userId), TICKET_TTL_SECONDS);
	}
}
