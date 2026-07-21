package com.example.demo.auction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Auction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal startingPrice;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal currentPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuctionStatus status;

	private String winnerName;

	@OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Bid> bids = new ArrayList<>();

	protected Auction() {
	}

	public Auction(String title, BigDecimal startingPrice) {
		this.title = title;
		this.startingPrice = startingPrice;
		this.currentPrice = startingPrice;
		this.status = AuctionStatus.OPEN;
	}

	public void update(String title, BigDecimal startingPrice) {
		this.title = title;
		this.startingPrice = startingPrice;
		if (bids.isEmpty()) {
			this.currentPrice = startingPrice;
		}
	}

	public void addBid(Bid bid) {
		bids.add(bid);
		currentPrice = bid.getAmount();
	}

	public boolean hasBids() {
		return !bids.isEmpty();
	}

	public void close(String winnerName) {
		this.status = AuctionStatus.CLOSED;
		this.winnerName = winnerName;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public BigDecimal getStartingPrice() {
		return startingPrice;
	}

	public BigDecimal getCurrentPrice() {
		return currentPrice;
	}

	public AuctionStatus getStatus() {
		return status;
	}

	public String getWinnerName() {
		return winnerName;
	}
}
