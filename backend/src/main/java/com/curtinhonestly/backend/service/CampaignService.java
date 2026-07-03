package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.CampaignEntry;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CampaignAdminDTO;
import com.curtinhonestly.backend.dto.CampaignEntryAdminDTO;
import com.curtinhonestly.backend.dto.CampaignEntrySummaryDTO;
import com.curtinhonestly.backend.dto.CampaignValidationDTO;
import com.curtinhonestly.backend.repo.CampaignEntryRepo;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class CampaignService {

    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_SUFFIX_LENGTH = 8;

    private final CampaignRepo campaignRepo;
    private final CampaignEntryRepo campaignEntryRepo;
    private final UserRepo userRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    public Optional<Campaign> resolveCampaignForRegistration(String ref, String promoCode) {
        Campaign byRef = normalize(ref)
                .flatMap(campaignRepo::findBySlugIgnoreCase)
                .orElse(null);
        Campaign byCode = normalize(promoCode)
                .flatMap(campaignRepo::findByCodeIgnoreCase)
                .orElse(null);

        if (byRef != null && byCode != null && !byRef.getId().equals(byCode.getId())) {
            throw new IllegalArgumentException("Referral link and promo code belong to different campaigns.");
        }

        Campaign campaign = byRef != null ? byRef : byCode;
        return Optional.ofNullable(campaign);
    }

    public CampaignValidationDTO validateCampaign(String ref, String promoCode) {
        Optional<Campaign> campaignOpt = resolveCampaignForRegistration(ref, promoCode);
        if (campaignOpt.isEmpty()) {
            return new CampaignValidationDTO(false, "Campaign not found.", null, null, null);
        }

        Campaign campaign = campaignOpt.get();
        String message = validateCampaignState(campaign, true);
        if (message != null) {
            return new CampaignValidationDTO(false, message, campaign.getName(), campaign.getPrizeDescription(), campaign.getEndsAt());
        }

        return new CampaignValidationDTO(
                true,
                "Campaign is active.",
                campaign.getName(),
                campaign.getPrizeDescription(),
                campaign.getEndsAt()
        );
    }

    public Optional<CampaignEntry> tryCreateEntryForReview(User user, Review review) {
        if (user.getCampaign() == null || user.isBanned() || !user.isVerifiedStudent()) {
            return Optional.empty();
        }

        Campaign campaign = user.getCampaign();
        if (!isWithinWindow(campaign, review.getCreatedAt())) {
            return Optional.empty();
        }

        String validationMessage = validateCampaignState(campaign, false);
        if (validationMessage != null) {
            return Optional.empty();
        }

        if (review.getReviewText() == null || review.getReviewText().trim().length() < campaign.getMinReviewLength()) {
            return Optional.empty();
        }

        if (campaignEntryRepo.existsByReview_Id(review.getId())) {
            return Optional.empty();
        }

        long existingEntries = campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId());
        if (existingEntries >= campaign.getMaxEntriesPerUser()) {
            return Optional.empty();
        }

        CampaignEntry entry = new CampaignEntry();
        entry.setCampaign(campaign);
        entry.setUser(user);
        entry.setReview(review);
        entry.setEntryToken(generateUniqueEntryToken());

        CampaignEntry saved = campaignEntryRepo.save(entry);
        log.info("Campaign entry {} created for user {} on review {}", saved.getEntryToken(), user.getId(), review.getId());
        return Optional.of(saved);
    }

    public List<CampaignEntrySummaryDTO> getEntriesForUser(User user) {
        return campaignEntryRepo.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(entry -> new CampaignEntrySummaryDTO(
                        entry.getEntryToken(),
                        entry.getCampaign().getName(),
                        entry.getReview().getUnit().getCode(),
                        entry.getCreatedAt()
                ))
                .toList();
    }

    public List<CampaignAdminDTO> listCampaigns() {
        return campaignRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminDTO)
                .toList();
    }

    public CampaignAdminDTO createCampaign(
            String slug,
            String code,
            String name,
            String prizeDescription,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            int minReviewLength,
            int maxEntriesPerUser
    ) {
        String normalizedSlug = requireNormalized(slug, "Campaign slug");
        String normalizedCode = requireNormalized(code, "Promo code").toUpperCase();

        if (campaignRepo.findBySlugIgnoreCase(normalizedSlug).isPresent()) {
            throw new IllegalArgumentException("A campaign with that slug already exists.");
        }
        if (campaignRepo.findByCodeIgnoreCase(normalizedCode).isPresent()) {
            throw new IllegalArgumentException("A campaign with that promo code already exists.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Campaign end date must be after the start date.");
        }

        if (minReviewLength <= 0) {
            minReviewLength = 50;
        }
        if (maxEntriesPerUser <= 0) {
            maxEntriesPerUser = 5;
        }

        Campaign campaign = new Campaign();
        campaign.setSlug(normalizedSlug);
        campaign.setCode(normalizedCode);
        campaign.setName(name.trim());
        campaign.setPrizeDescription(prizeDescription != null ? prizeDescription.trim() : null);
        campaign.setStartsAt(startsAt);
        campaign.setEndsAt(endsAt);
        campaign.setMaxRedemptions(maxRedemptions);
        campaign.setMinReviewLength(minReviewLength);
        campaign.setMaxEntriesPerUser(maxEntriesPerUser);
        campaign.setActive(true);

        return toAdminDTO(campaignRepo.save(campaign));
    }

    public CampaignAdminDTO setCampaignActive(String campaignId, boolean active) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found."));
        campaign.setActive(active);
        return toAdminDTO(campaignRepo.save(campaign));
    }

    public List<CampaignEntryAdminDTO> listEntriesForCampaign(String campaignId) {
        if (!campaignRepo.existsById(campaignId)) {
            throw new IllegalArgumentException("Campaign not found.");
        }

        return campaignEntryRepo.findByCampaign_IdOrderByCreatedAtDesc(campaignId).stream()
                .map(entry -> new CampaignEntryAdminDTO(
                        entry.getId(),
                        entry.getEntryToken(),
                        entry.getUser().getEmail(),
                        entry.getReview().getUnit().getCode(),
                        entry.getCreatedAt()
                ))
                .toList();
    }

    private CampaignAdminDTO toAdminDTO(Campaign campaign) {
        return new CampaignAdminDTO(
                campaign.getId(),
                campaign.getSlug(),
                campaign.getCode(),
                campaign.getName(),
                campaign.getPrizeDescription(),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                campaign.isActive(),
                campaign.getMaxRedemptions(),
                campaign.getMinReviewLength(),
                campaign.getMaxEntriesPerUser(),
                userRepo.countByCampaign_Id(campaign.getId()),
                campaignEntryRepo.countByCampaign_Id(campaign.getId()),
                campaign.getCreatedAt()
        );
    }

    private String validateCampaignState(Campaign campaign, boolean checkRedemptions) {
        if (!campaign.isActive()) {
            return "This campaign is no longer active.";
        }
        Instant now = Instant.now();
        if (now.isBefore(campaign.getStartsAt())) {
            return "This campaign has not started yet.";
        }
        if (now.isAfter(campaign.getEndsAt())) {
            return "This campaign has ended.";
        }
        if (checkRedemptions && campaign.getMaxRedemptions() != null) {
            long currentSignups = userRepo.countByCampaign_Id(campaign.getId());
            if (currentSignups >= campaign.getMaxRedemptions()) {
                return "This campaign has reached its signup limit.";
            }
        }
        return null;
    }

    private boolean isWithinWindow(Campaign campaign, Instant timestamp) {
        return !timestamp.isBefore(campaign.getStartsAt()) && !timestamp.isAfter(campaign.getEndsAt());
    }

    private String generateUniqueEntryToken() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String token = "CH-" + randomTokenSuffix();
            if (campaignEntryRepo.findByEntryTokenIgnoreCase(token).isEmpty()) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to generate a unique campaign entry token.");
    }

    private String randomTokenSuffix() {
        StringBuilder builder = new StringBuilder(TOKEN_SUFFIX_LENGTH);
        for (int i = 0; i < TOKEN_SUFFIX_LENGTH; i++) {
            builder.append(TOKEN_CHARS.charAt(secureRandom.nextInt(TOKEN_CHARS.length())));
        }
        return builder.toString();
    }

    private Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private String requireNormalized(String value, String label) {
        return normalize(value).orElseThrow(() -> new IllegalArgumentException(label + " is required."));
    }
}
