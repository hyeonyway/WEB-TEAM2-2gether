package com.dbidding.global.security;

public interface TicketProvider {

	String issue(Integer userId);

	Integer validateAndConsume(String ticket);
}
