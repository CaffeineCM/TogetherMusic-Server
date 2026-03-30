package com.togethermusic.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KugouQrLoginStartResponse {
    private String key;
    private String qrUrl;
    private String qrImage;
}
