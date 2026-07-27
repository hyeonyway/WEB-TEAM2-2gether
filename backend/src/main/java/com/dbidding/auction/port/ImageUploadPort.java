package com.dbidding.auction.port;

import java.util.List;

public interface ImageUploadPort {
    List<ResolvedImage> resolveImages(List<String> uploadTokens);

    record ResolvedImage(
            String imagePath,
            int sortOrder,
            boolean representative
    ) {
    }
}
