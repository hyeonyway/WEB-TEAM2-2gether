package com.dbidding.global.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dbidding.global.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InMemoryTicketProvider implements TicketProvider {

	private static final Duration TICKET_TTL = Duration.ofSeconds(30);

	private final ConcurrentMap<String, TicketEntry> tickets = new ConcurrentHashMap<>();
	private final Clock clock;

	@Override
	public String issue(Integer userId) {
		String ticket = UUID.randomUUID().toString();
		tickets.put(ticket, new TicketEntry(userId, clock.instant().plus(TICKET_TTL)));
		return ticket;
	}

	@Override
	public long ticketTtlSeconds() {
		return TICKET_TTL.toSeconds();
	}

	@Override
	public Integer validateAndConsume(String ticket) {
		if (ticket == null || ticket.isBlank()) {
			throw new UnauthorizedException();
		}

		TicketEntry entry = tickets.remove(ticket);
		if (entry == null || !clock.instant().isBefore(entry.expiresAt())) {
			throw new UnauthorizedException();
		}
		return entry.userId();
	}

	@Scheduled(fixedDelay = 60_000)
	void removeExpiredTickets() {
		Instant now = clock.instant();
		tickets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
	}

	int ticketCount() {
		return tickets.size();
	}

	private record TicketEntry(Integer userId, Instant expiresAt) {
	}
}
