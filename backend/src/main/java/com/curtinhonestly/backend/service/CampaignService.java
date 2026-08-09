package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.CampaignEntry;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CampaignAdminDTO;
import com.curtinhonestly.backend.dto.CampaignEntryAdminDTO;
import com.curtinhonestly.backend.dto.CampaignEntrySummaryDTO;
import com.curtinhonestly.backend.dto.CampaignProgressDTO;
import com.curtinhonestly.backend.dto.CampaignValidationDTO;
import com.curtinhonestly.backend.repo.CampaignEntryRepo;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.ReviewLikeRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
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
    private final ReviewRepo reviewRepo;
    private final ReviewLikeRepo reviewLikeRepo;
    private final UserService userService;
    private final SecureRandom secureRandom = new SecureRandom();

    public record CampaignAwardResult(Optional<CampaignEntry> newEntry, CampaignProgressDTO progress) {}

    // Resolves attribution, locks the campaign row, re-validates its state under
    // that lock, then creates the user - all in this one transaction. Holding the
    // lock across the whole count-and-insert (not just the initial read) is what
    // stops two concurrent registrations from both winning the last redemption slot.
    public User registerUserWithCampaign(String email, String password, String ref, String promoCode) {
        boolean hasAttribution = normalize(ref).isPresent() || normalize(promoCode).isPresent();
        if (!hasAttribution) {
            return userService.createUser(email, password, null, null);
        }

        Campaign resolved = resolveCampaignForRegistration(ref, promoCode)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found. Check your referral link or promo code."));

        // Tracking-only referral links carry no reward and no eligibility gates. We
        // record the attribution on the user (registeredViaRef = the link's slug) but
        // deliberately do NOT enrol them in the campaign, so the draw-entry machinery
        // and the student-facing "Campaign" section stay untouched. Signups and
        // reviews are then counted off registeredViaRef, not campaign membership.
        if (resolved.isTrackingOnly()) {
            return userService.createUser(email, password, null, resolved.getSlug());
        }

        Campaign locked = campaignRepo.findByIdForUpdate(resolved.getId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found. Check your referral link or promo code."));

        String stateError = validateCampaignState(locked, true);
        if (stateError != null) {
            throw new IllegalArgumentException(stateError);
        }

        return userService.createUser(email, password, locked, ref);
    }

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

    // Not-found, expired, not-started, and limit-reached all return this same
    // response so an unauthenticated caller can't enumerate which promo codes
    // exist vs. which have simply run out (audit #10).
    private static final String INVALID_CAMPAIGN_MESSAGE = "This referral link or promo code is invalid or no longer available.";

    public CampaignValidationDTO validateCampaign(String ref, String promoCode) {
        Optional<Campaign> campaignOpt = resolveCampaignForRegistration(ref, promoCode);
        if (campaignOpt.isEmpty()) {
            return new CampaignValidationDTO(false, INVALID_CAMPAIGN_MESSAGE, null, null, null);
        }

        Campaign campaign = campaignOpt.get();
        // Tracking-only links have no prize/campaign to advertise. Report them as
        // "not a promotable campaign" so the register page shows no banner — the
        // signup is still attributed server-side on /auth/register.
        if (campaign.isTrackingOnly()) {
            return new CampaignValidationDTO(false, INVALID_CAMPAIGN_MESSAGE, null, null, null);
        }

        String message = validateCampaignState(campaign, true);
        if (message != null) {
            return new CampaignValidationDTO(false, INVALID_CAMPAIGN_MESSAGE, null, null, null);
        }

        return new CampaignValidationDTO(
                true,
                "Campaign is active.",
                campaign.getName(),
                campaign.getPrizeDescription(),
                campaign.getEndsAt()
        );
    }

    // Records an attributed visit for a referral link. Best-effort: an unknown slug
    // updates nothing and is silently ignored, so a stale or mistyped link never errors.
    public void recordVisit(String ref) {
        normalize(ref).ifPresent(campaignRepo::incrementVisitCountBySlug);
    }

    public CampaignProgressDTO getCampaignProgress(User user) {
        if (user.getCampaign() == null) {
            return null;
        }
        return buildProgress(user, user.getCampaign());
    }

    public CampaignAwardResult tryAwardCampaignEntries(User user, Review triggeringReview) {
        if (user.getCampaign() == null || user.isBanned()) {
            return new CampaignAwardResult(Optional.empty(), null);
        }

        Campaign campaign = user.getCampaign();
        // Defensive: tracking-only campaigns never enrol members, but if one ever
        // does (e.g. a campaign flipped to trackingOnly after signups), award nothing.
        if (campaign.isTrackingOnly()) {
            return new CampaignAwardResult(Optional.empty(), null);
        }
        CampaignProgressDTO progress = buildProgress(user, campaign);

        if (campaign.isRequireVerifiedStudent() && !user.isVerifiedStudent()) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        if (!userMeetsLikeGiveRequirement(user, campaign)) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        if (!reviewQualifies(triggeringReview, campaign)) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        if (validateCampaignState(campaign, false) != null) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        // A unit whose review already produced an entry can't trigger another one.
        // Without this, deleting and resubmitting a review for the same unit would
        // free up the (user_id, unit_id) slot and let qualifyingCount recount it,
        // looping indefinitely for unlimited entries.
        if (campaignEntryRepo.existsByCampaign_IdAndUser_IdAndUnit_Id(campaign.getId(), user.getId(), triggeringReview.getUnit().getId())) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        long qualifyingCount = countQualifyingReviews(user, campaign);
        int requiredReviewCount = Math.max(1, campaign.getRequiredReviewCount());
        int maxEntriesPerUser = Math.max(1, campaign.getMaxEntriesPerUser());
        long entriesShouldHave = Math.min(qualifyingCount / requiredReviewCount, maxEntriesPerUser);
        long existingEntries = campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId());
        long entriesToCreate = entriesShouldHave - existingEntries;

        Optional<CampaignEntry> newEntry = Optional.empty();
        if (entriesToCreate > 0) {
            CampaignEntry entry = new CampaignEntry();
            entry.setCampaign(campaign);
            entry.setUser(user);
            entry.setUnit(triggeringReview.getUnit());
            entry.setReview(triggeringReview);
            entry.setEntryToken(generateUniqueEntryToken());
            CampaignEntry saved = campaignEntryRepo.save(entry);
            newEntry = Optional.of(saved);
            log.info("Campaign entry {} created for user {} on review {}", saved.getEntryToken(), user.getId(), triggeringReview.getId());
        }

        progress = buildProgress(user, campaign);
        return new CampaignAwardResult(newEntry, progress);
    }

    /**
     * Recomputes whether a user is owed any campaign entries based on current
     * qualifying reviews + like thresholds. Used when likes push either
     * minLikesReceived (review author) or minLikesGiven (liker) over the line.
     */
    public CampaignAwardResult reconcileCampaignEntries(User user) {
        if (user == null || user.getCampaign() == null || user.isBanned()) {
            return new CampaignAwardResult(Optional.empty(), null);
        }

        Campaign campaign = user.getCampaign();
        Optional<CampaignEntry> latestEntry = Optional.empty();

        // Award all outstanding entries (e.g. a likes-given gate unlocking several
        // already-posted reviews at once), stopping when nothing new can be created.
        while (true) {
            CampaignAwardResult result = tryAwardNextOutstandingEntry(user, campaign);
            if (result.newEntry().isEmpty()) {
                return new CampaignAwardResult(latestEntry, result.progress());
            }
            latestEntry = result.newEntry();
        }
    }

    private CampaignAwardResult tryAwardNextOutstandingEntry(User user, Campaign campaign) {
        CampaignProgressDTO progress = buildProgress(user, campaign);

        if (campaign.isRequireVerifiedStudent() && !user.isVerifiedStudent()) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }
        if (!userMeetsLikeGiveRequirement(user, campaign)) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }
        if (validateCampaignState(campaign, false) != null) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        long qualifyingCount = countQualifyingReviews(user, campaign);
        int requiredReviewCount = Math.max(1, campaign.getRequiredReviewCount());
        int maxEntriesPerUser = Math.max(1, campaign.getMaxEntriesPerUser());
        long entriesShouldHave = Math.min(qualifyingCount / requiredReviewCount, maxEntriesPerUser);
        long existingEntries = campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId());
        if (entriesShouldHave <= existingEntries) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        Optional<Review> trigger = reviewRepo.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(review -> reviewQualifies(review, campaign))
                .filter(review -> !campaignEntryRepo.existsByCampaign_IdAndUser_IdAndUnit_Id(
                        campaign.getId(), user.getId(), review.getUnit().getId()))
                .findFirst();

        if (trigger.isEmpty()) {
            return new CampaignAwardResult(Optional.empty(), progress);
        }

        return tryAwardCampaignEntries(user, trigger.get());
    }

    public List<CampaignEntrySummaryDTO> getEntriesForUser(User user) {
        return campaignEntryRepo.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(entry -> new CampaignEntrySummaryDTO(
                        entry.getEntryToken(),
                        entry.getCampaign().getName(),
                        entry.getUnit().getCode(),
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
            int maxEntriesPerUser,
            boolean requireVerifiedStudent,
            int requiredReviewCount,
            int minLikesReceived,
            int minLikesGiven
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
            maxEntriesPerUser = 1;
        }
        if (requiredReviewCount <= 0) {
            requiredReviewCount = 1;
        }
        if (minLikesReceived < 0) {
            minLikesReceived = 0;
        }
        if (minLikesGiven < 0) {
            minLikesGiven = 0;
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
        campaign.setRequireVerifiedStudent(requireVerifiedStudent);
        campaign.setRequiredReviewCount(requiredReviewCount);
        campaign.setMinLikesReceived(minLikesReceived);
        campaign.setMinLikesGiven(minLikesGiven);
        campaign.setActive(true);

        return toAdminDTO(campaignRepo.save(campaign));
    }

    // Creates a tracking-only referral link: no prize, no requirement, no draw
    // entries — it just forwards to the app and attributes downstream signups and
    // reviews. Reuses the Campaign row/table; the reward fields are set to inert
    // defaults and ignored everywhere trackingOnly is checked. The window runs from
    // now to the far future so the link never "expires", and a placeholder code is
    // generated only to satisfy the unique/non-null constraint (it is never shown).
    public CampaignAdminDTO createReferralLink(String slug, String name, String landingPath) {
        String normalizedSlug = requireNormalized(slug, "Referral link code");

        // A link's slug is resolved as a ?ref= value, which shares a namespace with
        // promo codes (resolveCampaignForRegistration matches ref->slug and code->code).
        // Reject a slug that collides with any existing slug OR code, so ?ref=X can
        // never become ambiguous against a reward campaign's code.
        if (campaignRepo.findBySlugIgnoreCase(normalizedSlug).isPresent()
                || campaignRepo.findByCodeIgnoreCase(normalizedSlug).isPresent()) {
            throw new IllegalArgumentException("A campaign or referral link with that code already exists.");
        }

        Campaign campaign = new Campaign();
        campaign.setSlug(normalizedSlug);
        campaign.setCode(generateUniquePlaceholderCode());
        campaign.setName(normalize(name).orElse(normalizedSlug));
        campaign.setPrizeDescription(null);
        campaign.setStartsAt(Instant.now());
        campaign.setEndsAt(Instant.now().plus(Duration.ofDays(3650)));
        campaign.setMaxRedemptions(null);
        campaign.setMinReviewLength(0);
        campaign.setMaxEntriesPerUser(1);
        campaign.setRequireVerifiedStudent(false);
        campaign.setRequiredReviewCount(1);
        campaign.setMinLikesReceived(0);
        campaign.setMinLikesGiven(0);
        campaign.setTrackingOnly(true);
        campaign.setLandingPath(normalizeLandingPath(landingPath));
        campaign.setActive(true);

        return toAdminDTO(campaignRepo.save(campaign));
    }

    // Normalizes the admin-chosen landing page to a safe, site-relative path. Any
    // pasted query/fragment is dropped (the ?ref= is appended when the link is
    // built), and absolute or protocol-relative URLs are rejected so a link can't
    // forward off-site. Blank defaults to the site root.
    private String normalizeLandingPath(String landingPath) {
        String path = landingPath == null ? "" : landingPath.trim();
        int queryAt = path.indexOf('?');
        if (queryAt >= 0) {
            path = path.substring(0, queryAt);
        }
        int fragmentAt = path.indexOf('#');
        if (fragmentAt >= 0) {
            path = path.substring(0, fragmentAt);
        }
        path = path.trim();
        if (path.isEmpty()) {
            return "/";
        }
        if (path.contains("://") || path.startsWith("//")) {
            throw new IllegalArgumentException("Landing page must be a path on the site, e.g. / or /units/COMP1000.");
        }
        return path.startsWith("/") ? path : "/" + path;
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
                        entry.getUnit().getCode(),
                        entry.getCreatedAt()
                ))
                .toList();
    }

    private long countQualifyingReviews(User user, Campaign campaign) {
        return reviewRepo.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(review -> reviewQualifies(review, campaign))
                .count();
    }

    private boolean reviewQualifies(Review review, Campaign campaign) {
        if (!isWithinWindow(campaign, review.getCreatedAt())) {
            return false;
        }
        if (review.getReviewText() == null
                || review.getReviewText().trim().length() < campaign.getMinReviewLength()) {
            return false;
        }
        return review.getLikeCount() >= Math.max(0, campaign.getMinLikesReceived());
    }

    private boolean userMeetsLikeGiveRequirement(User user, Campaign campaign) {
        int required = Math.max(0, campaign.getMinLikesGiven());
        if (required == 0) {
            return true;
        }
        return countLikesGivenInWindow(user, campaign) >= required;
    }

    private long countLikesGivenInWindow(User user, Campaign campaign) {
        return reviewLikeRepo.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
                user.getId(), campaign.getStartsAt(), campaign.getEndsAt());
    }

    private CampaignProgressDTO buildProgress(User user, Campaign campaign) {
        long qualifyingReviews = countQualifyingReviews(user, campaign);
        int requiredReviewCount = Math.max(1, campaign.getRequiredReviewCount());
        int maxEntriesPerUser = Math.max(1, campaign.getMaxEntriesPerUser());
        long existingEntries = campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId());
        long computedEntries = userMeetsLikeGiveRequirement(user, campaign)
                ? Math.min(qualifyingReviews / requiredReviewCount, maxEntriesPerUser)
                : 0;
        // Never report fewer earned entries than tokens already issued (likes/reviews
        // dropping below a threshold do not revoke draw entries).
        long entriesEarned = Math.max(computedEntries, existingEntries);

        return new CampaignProgressDTO(
                (int) qualifyingReviews,
                requiredReviewCount,
                (int) entriesEarned,
                maxEntriesPerUser,
                campaign.isRequireVerifiedStudent(),
                Math.max(0, campaign.getMinLikesReceived()),
                Math.max(0, campaign.getMinLikesGiven()),
                (int) countLikesGivenInWindow(user, campaign)
        );
    }

    private CampaignAdminDTO toAdminDTO(Campaign campaign) {
        // Tracking-only links attribute off registeredViaRef (the user is never
        // enrolled in the campaign); reward campaigns attribute off membership.
        long signupCount = campaign.isTrackingOnly()
                ? userRepo.countByRegisteredViaRefIgnoreCase(campaign.getSlug())
                : userRepo.countByCampaign_Id(campaign.getId());
        long reviewCount = campaign.isTrackingOnly()
                ? reviewRepo.countByUser_RegisteredViaRefIgnoreCase(campaign.getSlug())
                : reviewRepo.countByUser_Campaign_Id(campaign.getId());

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
                campaign.isRequireVerifiedStudent(),
                campaign.getRequiredReviewCount(),
                campaign.getMinLikesReceived(),
                campaign.getMinLikesGiven(),
                campaign.isTrackingOnly(),
                campaign.getVisitCount(),
                campaign.getLandingPath(),
                signupCount,
                reviewCount,
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
        log.error("Failed to generate a unique campaign entry token after {} attempts", 10);
        throw new CampaignEntryTokenExhaustedException("Unable to generate a unique entry token right now. Please try again.");
    }

    private String generateUniquePlaceholderCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "REF-" + randomTokenSuffix();
            if (campaignRepo.findByCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique referral link code. Please try again.");
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
