package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class TipDTO {
    private String id;
    private String text;
    private boolean authorVerified;
    private boolean ownedByCurrentUser;
    private Instant createdAt;
}
