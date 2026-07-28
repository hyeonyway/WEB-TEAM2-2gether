package com.dbidding.upload.controller;

import static org.mockito.BDDMockito.given;

import com.dbidding.upload.dto.ImageUploadResponses;
import com.dbidding.upload.service.UploadService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    @Test
    void 파일_목록으로_presignedURL_목록을_200으로_반환한다() throws Exception {
        given(uploadService.createPresignedUrls(org.mockito.ArgumentMatchers.any())).willReturn(
                new ImageUploadResponses.PresignedUrlResponse(
                        List.of(new ImageUploadResponses.PresignedUpload(
                                "https://example.com/signed", "uploads/2026/07/28/uuid.jpg", 300))
                )
        );

        mockMvc.perform(MockMvcRequestBuilders.post("/api/uploads/images/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"fileName\":\"card1.jpg\",\"contentType\":\"image/jpeg\"}]}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.uploads.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.uploads[0].upload_url").value("https://example.com/signed"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.uploads[0].upload_token").value("uploads/2026/07/28/uuid.jpg"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.uploads[0].expires_in_seconds").value(300));
    }

    @Test
    void files가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/uploads/images/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[]}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void files가_10개를_초과하면_400을_반환한다() throws Exception {
        String files = "\"fileName\":\"a.jpg\",\"contentType\":\"image/jpeg\"".repeat(1);
        StringBuilder body = new StringBuilder("{\"files\":[");
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                body.append(",");
            }
            body.append("{").append(files).append("}");
        }
        body.append("]}");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/uploads/images/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void contentType이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/uploads/images/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"fileName\":\"card1.jpg\",\"contentType\":\"\"}]}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }
}