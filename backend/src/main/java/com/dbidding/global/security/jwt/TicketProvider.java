package com.dbidding.global.security.jwt;

public interface TicketProvider {

	String issue(Integer userId);

	Integer validateAndConsume(String ticket);

	long ticketTtlSeconds();
}
