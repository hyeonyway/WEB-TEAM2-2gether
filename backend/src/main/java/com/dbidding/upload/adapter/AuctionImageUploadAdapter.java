package com.dbidding.upload.adapter;

import com.dbidding.auction.port.ImageUploadPort;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!auction-mock")
public class AuctionImageUploadAdapter implements ImageUploadPort {

    @Override
    public List<ResolvedImage> resolveImages(List<String> uploadTokens) {
        if (uploadTokens == null) {
            return List.of();
        }

        return IntStream.range(0, uploadTokens.size())
                .mapToObj(index -> resolve(uploadTokens.get(index), index))
                .toList();
    }

    private ResolvedImage resolve(String uploadToken, int sortOrder) {
        if (uploadToken == null || uploadToken.isBlank()) {
            throw new IllegalArgumentException("이미지 업로드 토큰이 비어 있습니다.");
        }
        return new ResolvedImage(uploadToken, sortOrder, sortOrder == 0);
    }
}
