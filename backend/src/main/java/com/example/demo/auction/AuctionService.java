package com.example.demo.auction;

import com.example.demo.auction.dto.AuctionCreateRequest;
import com.example.demo.auction.dto.AuctionResponse;
import com.example.demo.auction.dto.AuctionUpdateRequest;
import com.example.demo.auction.dto.BidCreateRequest;
import com.example.demo.auction.dto.BidResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuctionService {

	private final AuctionRepository auctionRepository;
	private final BidRepository bidRepository;

	public AuctionService(AuctionRepository auctionRepository, BidRepository bidRepository) {
		this.auctionRepository = auctionRepository;
		this.bidRepository = bidRepository;
	}

	@Transactional
	public AuctionResponse create(AuctionCreateRequest request) {
		Auction auction = new Auction(request.title().trim(), request.startingPrice());
		return AuctionResponse.from(auctionRepository.save(auction));
	}

	public List<AuctionResponse> findAll() {
		return auctionRepository.findAll().stream()
				.map(AuctionResponse::from)
				.toList();
	}

	public AuctionResponse findById(Long auctionId) {
		return AuctionResponse.from(getAuction(auctionId));
	}

	@Transactional
	public AuctionResponse update(Long auctionId, AuctionUpdateRequest request) {
		Auction auction = getAuction(auctionId);
		ensureOpen(auction);
		if (auction.hasBids()) {
			throw new BusinessException("입찰이 시작된 경매는 수정할 수 없습니다.");
		}

		auction.update(request.title().trim(), request.startingPrice());
		return AuctionResponse.from(auction);
	}

	@Transactional
	public void delete(Long auctionId) {
		Auction auction = getAuction(auctionId);
		auctionRepository.delete(auction);
	}

	@Transactional
	public BidResponse placeBid(Long auctionId, BidCreateRequest request) {
		Auction auction = getAuction(auctionId);
		ensureOpen(auction);
		if (request.amount().compareTo(auction.getCurrentPrice()) <= 0) {
			throw new BusinessException("입찰 금액은 현재 최고가보다 커야 합니다.");
		}

		Bid bid = new Bid(auction, request.bidderName().trim(), request.amount());
		auction.addBid(bid);
		Bid savedBid = bidRepository.save(bid);
		return BidResponse.from(savedBid, auctionId);
	}

	@Transactional
	public AuctionResponse close(Long auctionId) {
		Auction auction = getAuction(auctionId);
		ensureOpen(auction);

		String winnerName = bidRepository.findFirstByAuctionIdOrderByAmountDescIdAsc(auctionId)
				.map(Bid::getBidderName)
				.orElse(null);
		auction.close(winnerName);
		return AuctionResponse.from(auction);
	}

	private Auction getAuction(Long auctionId) {
		return auctionRepository.findById(auctionId)
				.orElseThrow(() -> new ResourceNotFoundException("경매를 찾을 수 없습니다. id=" + auctionId));
	}

	private void ensureOpen(Auction auction) {
		if (auction.getStatus() == AuctionStatus.CLOSED) {
			throw new BusinessException("이미 종료된 경매입니다.");
		}
	}
}
