package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.BoardPostRepo;
import com.curtinhonestly.backend.repo.BoardThreadRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Owner checks for the board @PreAuthorize expressions, mirroring
 * UnitTipSecurityService: the username comes off Authentication.getName(),
 * and anonymised content (author deleted their account) has no owner.
 */
@Service
@RequiredArgsConstructor
public class BoardSecurityService {

    private final BoardThreadRepo threadRepo;
    private final BoardPostRepo postRepo;

    public boolean isThreadOwner(String threadId, Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return false;
        }
        try {
            return threadRepo.findById(threadId)
                    .map(thread -> owns(thread.getAuthor(), authentication.getName()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPostOwner(String postId, Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return false;
        }
        try {
            return postRepo.findById(postId)
                    .map(post -> owns(post.getAuthor(), authentication.getName()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !"anonymousUser".equals(authentication.getName());
    }

    private static boolean owns(User author, String username) {
        return author != null && author.getEmail() != null && author.getEmail().equals(username);
    }
}
