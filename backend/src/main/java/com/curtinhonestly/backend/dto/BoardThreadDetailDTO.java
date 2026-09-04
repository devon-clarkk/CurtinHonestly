package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.BoardScope;

import java.time.Instant;
import java.util.List;

/**
 * A thread plus one page of its replies. Replies are paged at 100 so a long
 * thread never ships as one payload; postPage/postTotalPages drive the pager.
 */
public record BoardThreadDetailDTO(
        String id,
        BoardScope scope,
        String unitCode,
        String unitName,
        String title,
        String body,
        BoardAuthorDTO author,
        int replyCount,
        boolean pinned,
        boolean locked,
        boolean ownedByCurrentUser,
        boolean canEdit,
        Instant createdAt,
        Instant editedAt,
        Instant lastActivityAt,
        List<BoardPostDTO> posts,
        int postPage,
        int postTotalPages,
        long postTotal
) {}
