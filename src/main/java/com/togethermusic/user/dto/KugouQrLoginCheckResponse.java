package com.togethermusic.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KugouQrLoginCheckResponse {
    private int code;
    private String message;
    private boolean authorized;
    private String nickname;
}
