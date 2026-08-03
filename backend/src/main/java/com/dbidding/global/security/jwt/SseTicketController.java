package com.dbidding.global.security.jwt;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sse/tickets")
@RequiredArgsConstructor
public class SseTicketController {

	private final TicketProvider ticketProvider;

	@PostMapping
	public SseTicketResponse issue(@CurrentUser Integer userId) {
		return new SseTicketResponse(
			ticketProvider.issue(userId),
			ticketProvider.ticketTtlSeconds()
		);
	}
}
