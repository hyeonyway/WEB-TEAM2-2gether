package com.example.demo.auction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;

@Entity
public class Bid {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "auction_id", nullable = false)
	private Auction auction;

	@Column(nullable = false)
	private String bidderName;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	protected Bid() {
	}

	public Bid(Auction auction, String bidderName, BigDecimal amount) {
		this.auction = auction;
		this.bidderName = bidderName;
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public String getBidderName() {
		return bidderName;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
