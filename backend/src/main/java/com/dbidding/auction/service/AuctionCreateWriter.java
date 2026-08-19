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
 * 반면 {@code create()}는 메서드 전체가 이미 하나의 {@code @Transactional}로 묶여 있어,
 * 그 안에서 그대로 저장을 시도하면 유니크 제약 위반 시 JPA/Hibernate가 (애플리케이션이
 * 예외를 catch하더라도) 해당 트랜잭션을 rollback-only로 마킹해버린다 — 이후 같은
 * 트랜잭션에서 재조회를 시도하면 조회 자체는 성공할 수 있어도, 트랜잭션 커밋 시점에
 * Spring이 rollback-only를 감지해 {@code UnexpectedRollbackException}을 던진다.
 * 그래서 이 INSERT(및 함께 저장되어야 하는 이미지)만 {@code REQUIRES_NEW}로 별도의
 * 물리 트랜잭션에 태워, 실패하더라도 그 트랜잭션만 완전히 롤백되고 끝나게 하고, 호출부
 * ({@code create()})가 갖고 있던 원래 트랜잭션은 이 INSERT 실패로 오염되지 않도록 한다
 * (REQUIRES_NEW는 기존 트랜잭션을 suspend했다가 되돌려주므로).
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
     * (AuctionCommandService#create)의 원래 트랜잭션은 REPEATABLE READ 스냅샷을 이미
     * 첫 조회 시점에 고정해뒀을 수 있어(#393과 동일한 이유), 그 트랜잭션 그대로 재조회하면
     * 방금 경쟁자가 커밋한 행을 못 볼 위험이 있다. REQUIRES_NEW로 완전히 새 트랜잭션을 열어
     * 새 스냅샷으로 조회한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Auction> findAfterConflict(Integer sellerId, String idempotencyKey) {
        return auctionRepository.findBySellerIdAndCreateIdempotencyKey(sellerId, idempotencyKey);
    }
}
