package com.dbidding.auction.service;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AuctionCommandService.create()}의 INSERT만 별도 물리 트랜잭션으로 분리한다.
 * {@code auctions}의 {@code uk_auctions_user_idempotency} 유니크 제약(user_id,
 * idempotency_key) 때문에, 같은 유저가 같은 idempotencyKey로 동시에 두 번 요청하면
 * (더블클릭, 타임아웃 후 재시도 등) 둘 다 사전 조회에서는 "기존 레코드 없음"을 보고 이후
 * 하나는 저장에 성공하고 하나는 이 제약 위반으로 실패한다.
 *
 * {@link NotificationEventListener}(#saveAndPush)도 유니크 제약 레이스를 예외로 잡아
 * 재조회하는 동일 부류의 문제를 다루지만, 그 경로는 호출부(비동기 이벤트 리스너)에 애초에
 * 활성 트랜잭션이 없어 저장 호출과 재조회 호출이 자연스럽게 각자 독립된 트랜잭션으로 실행된다.
 * {@code AuctionCommandService.create()}도 현재는 그 자체가 {@code @Transactional}이
 * 아니므로(검증/카드 스냅샷 조회/이미지 업로드는 DB write가 필요 없고, Redis 프로필 경로는
 * JPA를 아예 쓰지 않는다) 실질적으로는 같은 상황이지만, 이 INSERT(및 함께 저장되어야 하는
 * 이미지)는 여전히 {@code REQUIRES_NEW}로 별도의 물리 트랜잭션에 태운다 — 만약 create()가
 * 향후 다시 트랜잭션으로 감싸이거나 트랜잭션이 이미 열린 컨텍스트에서 호출되더라도, 유니크
 * 제약 위반 시 JPA/Hibernate가 그 앰비언트 트랜잭션을 rollback-only로 마킹해 이후 재조회가
 * 커밋 시점에 {@code UnexpectedRollbackException}으로 실패하는 사태를 방지하기 위함이다.
 * REQUIRES_NEW 트랜잭션은 실패해도 그 자신만 완전히 롤백되고 끝나므로, 호출부에 활성
 * 트랜잭션이 있든 없든 항상 안전하다.
 */
@Service
@RequiredArgsConstructor
public class AuctionCreateWriter {

    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Auction save(Auction auction, List<ImageUploadPort.ResolvedImage> images) {
        Auction savedAuction = auctionRepository.save(auction);
        List<AuctionImage> auctionImages = images.stream()
                .sorted(Comparator.comparingInt(ImageUploadPort.ResolvedImage::sortOrder))
                .map(image -> new AuctionImage(savedAuction, image.imagePath()))
                .toList();
        auctionImageRepository.saveAll(auctionImages);
        return savedAuction;
    }

    /**
     * 위 {@link #save}가 유니크 제약 위반으로 실패한 직후의 재조회 전용. 호출부
     * (AuctionCommandService#create)는 현재 자체 트랜잭션을 열지 않지만, 만약 향후 활성
     * 트랜잭션이 있는 컨텍스트에서 호출되어 그 트랜잭션이 REPEATABLE READ 스냅샷을 이미
     * 고정해뒀다면(#393과 동일한 이유) 그 스냅샷 그대로 재조회 시 방금 경쟁자가 커밋한 행을
     * 못 볼 위험이 있다. REQUIRES_NEW로 완전히 새 트랜잭션을 열어 새 스냅샷으로 조회함으로써
     * 호출부의 트랜잭션 상태와 무관하게 항상 최신 커밋을 본다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Auction> findAfterConflict(Integer sellerId, String idempotencyKey) {
        return auctionRepository.findBySellerIdAndCreateIdempotencyKey(sellerId, idempotencyKey);
    }
}
