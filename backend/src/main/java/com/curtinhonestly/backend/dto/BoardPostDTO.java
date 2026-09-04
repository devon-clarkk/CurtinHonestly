package com.curtinhonestly.backend.dto;

import java.time.Instant;

/**
 * A reply as the thread view renders it. A soft-deleted post keeps its slot
 * with deleted=true, a "[removed]" body and no author, so replies that came
 * after it still read in context.
 *
 * @param op                 true when the post author is also the thread author
 * @param ownedByCurrentUser false for anonymous callers
 * @param canEdit            owner inside the edit window, or an admin
 */
public record BoardPostDTO(
        String id,
        String threadId,
        String body,
        BoardAuthorDTO author,
        boolean op,
        boolean deleted,
        boolean ownedByCurrentUser,
        boolean canEdit,
        Instant createdAt,
        Instant editedAt
) {}
