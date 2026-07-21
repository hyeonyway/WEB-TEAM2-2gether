package com.example.demo.auction;

import com.example.demo.auction.dto.AuctionCreateRequest;
import com.example.demo.auction.dto.AuctionResponse;
import com.example.demo.auction.dto.AuctionUpdateRequest;
import com.example.demo.auction.dto.BidCreateRequest;
import com.example.demo.auction.dto.BidResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Auctions", description = "간단한 경매 CRUD 및 입찰 API")
@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

	private final AuctionService auctionService;

	public AuctionController(AuctionService auctionService) {
		this.auctionService = auctionService;
	}

	@Operation(summary = "경매 생성")
	@PostMapping
	public ResponseEntity<AuctionResponse> create(@Valid @RequestBody AuctionCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(auctionService.create(request));
	}

	@Operation(summary = "경매 목록 조회")
	@GetMapping
	public List<AuctionResponse> findAll() {
		return auctionService.findAll();
	}

	@Operation(summary = "경매 단건 조회")
	@GetMapping("/{auctionId}")
	public AuctionResponse findById(@PathVariable Long auctionId) {
		return auctionService.findById(auctionId);
	}

	@Operation(summary = "경매 수정", description = "입찰이 시작되기 전인 경매만 수정할 수 있습니다.")
	@PutMapping("/{auctionId}")
	public AuctionResponse update(
			@PathVariable Long auctionId,
			@Valid @RequestBody AuctionUpdateRequest request
	) {
		return auctionService.update(auctionId, request);
	}

	@Operation(summary = "경매 삭제")
	@DeleteMapping("/{auctionId}")
	public ResponseEntity<Void> delete(@PathVariable Long auctionId) {
		auctionService.delete(auctionId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "입찰", description = "현재 최고가보다 높은 금액만 입찰할 수 있습니다.")
	@PostMapping("/{auctionId}/bids")
	public ResponseEntity<BidResponse> placeBid(
			@PathVariable Long auctionId,
			@Valid @RequestBody BidCreateRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(auctionService.placeBid(auctionId, request));
	}

	@Operation(summary = "경매 종료", description = "현재 최고 입찰자가 낙찰자로 정해집니다.")
	@PostMapping("/{auctionId}/close")
	public AuctionResponse close(@PathVariable Long auctionId) {
		return auctionService.close(auctionId);
	}
}
