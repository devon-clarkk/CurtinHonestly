package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.ReferralLink;
import com.curtinhonestly.backend.dto.ReferralLinkAdminDTO;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.ReferralLinkRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Referral links: a shareable slug that enrols whoever signs up through it into
 * zero or more campaigns at once (multiple draws under one link). A link with no
 * campaigns is a pure tracking link.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class ReferralLinkService {

    private final ReferralLinkRepo referralLinkRepo;
    private final CampaignRepo campaignRepo;
    private final UserRepo userRepo;
    private final ReviewRepo reviewRepo;

    public ReferralLinkAdminDTO createReferralLink(String slug, String name, String landingPath, List<String> campaignIds) {
        String normalizedSlug = requireNormalized(slug, "Referral link code");

        // The slug is resolved as a ?ref= value, ahead of campaign slugs/codes, so it
        // must not collide with any of those namespaces or an existing link.
        if (referralLinkRepo.findBySlugIgnoreCase(normalizedSlug).isPresent()
                || campaignRepo.findBySlugIgnoreCase(normalizedSlug).isPresent()
                || campaignRepo.findByCodeIgnoreCase(normalizedSlug).isPresent()) {
            throw new IllegalArgumentException("A campaign or referral link with that code already exists.");
        }

        Set<Campaign> campaigns = new LinkedHashSet<>();
        if (campaignIds != null) {
            for (String id : campaignIds) {
                Campaign campaign = campaignRepo.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
                if (campaign.isTrackingOnly()) {
                    throw new IllegalArgumentException("Tracking-only campaigns can't be attached to a referral link.");
                }
                campaigns.add(campaign);
            }
        }

        ReferralLink link = new ReferralLink();
        link.setSlug(normalizedSlug);
        link.setName(normalize(name) != null ? normalize(name) : normalizedSlug);
        link.setLandingPath(normalizeLandingPath(landingPath));
        link.setCampaigns(campaigns);
        link.setActive(true);

        return toAdminDTO(referralLinkRepo.save(link));
    }

    public List<ReferralLinkAdminDTO> listReferralLinks() {
        return referralLinkRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminDTO)
                .toList();
    }

    public ReferralLinkAdminDTO setActive(String id, boolean active) {
        ReferralLink link = referralLinkRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Referral link not found."));
        link.setActive(active);
        return toAdminDTO(referralLinkRepo.save(link));
    }

    private ReferralLinkAdminDTO toAdminDTO(ReferralLink link) {
        // Signups/reviews are attributed via registeredViaRef = the link slug.
        long signups = userRepo.countByRegisteredViaRefIgnoreCase(link.getSlug());
        long reviews = reviewRepo.countByUser_RegisteredViaRefIgnoreCase(link.getSlug());
        List<ReferralLinkAdminDTO.CampaignRef> campaignRefs = link.getCampaigns().stream()
                .map(c -> new ReferralLinkAdminDTO.CampaignRef(c.getId(), c.getName()))
                .toList();
        return new ReferralLinkAdminDTO(
                link.getId(),
                link.getSlug(),
                link.getName(),
                link.getLandingPath(),
                link.isActive(),
                link.getVisitCount(),
                signups,
                reviews,
                campaignRefs,
                link.getCreatedAt());
    }

    // Site-relative path only: strips any pasted query/fragment, rejects absolute or
    // protocol-relative URLs so a link can't forward off-site, blank defaults to "/".
    private String normalizeLandingPath(String landingPath) {
        String path = landingPath == null ? "" : landingPath.trim();
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        cut = path.indexOf('#');
        if (cut >= 0) path = path.substring(0, cut);
        path = path.trim();
        if (path.isEmpty()) {
            return "/";
        }
        if (path.contains("://") || path.startsWith("//")) {
            throw new IllegalArgumentException("Landing page must be a path on the site, e.g. / or /units/COMP1000.");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireNormalized(String value, String label) {
        String n = normalize(value);
        if (n == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return n;
    }
}
