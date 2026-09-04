package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ResourceKind;
import com.curtinhonestly.backend.domain.ResourceStatus;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.UnitResourceLink;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AdminUnitResourcePreviewDTO;
import com.curtinhonestly.backend.dto.UnitResourceLinkDTO;
import com.curtinhonestly.backend.dto.UnitResourceLinkSuggestionRequest;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitResourceLinkRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.service.UnitResourceLinkService.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Matching, de-duplication and scope labels are pure and tested directly on
 * {@link Rule}s. The suggestion path is exercised through Mockito repos.
 */
@ExtendWith(MockitoExtension.class)
class UnitResourceLinkServiceTest {

    @Mock UnitResourceLinkRepo repo;
    @Mock UnitRepo unitRepo;
    @Mock UserRepo userRepo;
    @Mock ProfanityFilterService profanityFilterService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UnitResourceLinkService service() {
        return new UnitResourceLinkService(repo, unitRepo, userRepo, profanityFilterService);
    }

    // -------------------------------------------------------------- fixtures

    private static Unit unit(String id, String code, Faculty faculty, UnitLevel level) {
        Unit u = new Unit();
        u.setId(id);
        u.setCode(code);
        u.setName(code + " name");
        u.setFaculty(faculty);
        u.setLevel(level);
        return u;
    }

    private static final Unit COMP1000 = unit("u-comp", "COMP1000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE);
    private static final Unit ISAD5000 = unit("u-isad", "ISAD5000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.POSTGRADUATE);
    private static final Unit MGMT1000 = unit("u-mgmt", "MGMT1000", Faculty.BUSINESS_AND_LAW, UnitLevel.UNDERGRADUATE);

    private static Rule rule(String id, String title, String url, ResourceKind kind, String targetUnitId,
                             List<String> prefixes, Faculty faculty, UnitLevel level, int sortOrder,
                             ResourceStatus status) {
        return new Rule(id, title, url, null, kind, targetUnitId, prefixes, faculty, level, sortOrder, status);
    }

    private static Rule approved(String id, String title, String url, ResourceKind kind, List<String> prefixes,
                                 Faculty faculty, UnitLevel level) {
        return rule(id, title, url, kind, null, prefixes, faculty, level, 0, ResourceStatus.APPROVED);
    }

    private static Rule forUnit(String id, String title, String url, String unitId) {
        return rule(id, title, url, ResourceKind.WEBSITE, unitId, List.of(), null, null, 0, ResourceStatus.APPROVED);
    }

    private static List<String> ids(List<UnitResourceLinkDTO> items) {
        return items.stream().map(UnitResourceLinkDTO::id).toList();
    }

    // -------------------------------------------------------------- matching

    @Test
    void unitSpecificRowOnlyMatchesItsOwnUnit() {
        Rule r = forUnit("r1", "Lecturer notes", "https://notes.example.com", "u-comp");

        assertThat(ids(UnitResourceLinkService.select(List.of(r), COMP1000))).containsExactly("r1");
        assertThat(UnitResourceLinkService.select(List.of(r), ISAD5000)).isEmpty();
    }

    @Test
    void prefixRuleMatchesCaseInsensitivelyOnTheStartOfTheCode() {
        Rule r = approved("r1", "ComSSA Discord", "https://discord.gg/comssa", ResourceKind.DISCORD,
                List.of("COMP", "ISAD"), null, null);
        Unit lowerCase = unit("u-x", "comp2003", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE);

        assertThat(UnitResourceLinkService.select(List.of(r), COMP1000)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), ISAD5000)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), lowerCase)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), MGMT1000)).isEmpty();
    }

    @Test
    void prefixMustMatchTheStartNotTheMiddle() {
        Rule r = approved("r1", "x", "https://a.example.com", ResourceKind.OTHER, List.of("MP10"), null, null);
        assertThat(UnitResourceLinkService.select(List.of(r), COMP1000)).isEmpty();
    }

    @Test
    void facultyRuleMatchesOnlyThatFaculty() {
        Rule r = approved("r1", "Science hub", "https://sci.example.com", ResourceKind.WEBSITE,
                List.of(), Faculty.SCIENCE_AND_ENGINEERING, null);

        assertThat(UnitResourceLinkService.select(List.of(r), COMP1000)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), ISAD5000)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), MGMT1000)).isEmpty();
    }

    @Test
    void levelRuleMatchesOnlyThatLevel() {
        Rule r = approved("r1", "Postgrad society", "https://pg.example.com", ResourceKind.CLUB,
                List.of(), null, UnitLevel.POSTGRADUATE);

        assertThat(UnitResourceLinkService.select(List.of(r), ISAD5000)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), COMP1000)).isEmpty();
    }

    @Test
    void combinedCriteriaMustAllHold() {
        Rule r = approved("r1", "UG computing", "https://ug.example.com", ResourceKind.STUDY_GROUP,
                List.of("COMP", "ISAD"), Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE);

        assertThat(UnitResourceLinkService.select(List.of(r), COMP1000)).hasSize(1);
        // Prefix and faculty match, level does not.
        assertThat(UnitResourceLinkService.select(List.of(r), ISAD5000)).isEmpty();
        // Faculty wrong, prefix wrong.
        assertThat(UnitResourceLinkService.select(List.of(r), MGMT1000)).isEmpty();
    }

    @Test
    void ruleWithNoCriteriaIsSiteWide() {
        Rule r = approved("r1", "Curtin Guild", "https://guild.example.com", ResourceKind.CLUB, List.of(), null, null);

        for (Unit u : List.of(COMP1000, ISAD5000, MGMT1000)) {
            assertThat(UnitResourceLinkService.select(List.of(r), u)).hasSize(1);
        }
    }

    @Test
    void onlyApprovedRowsAreSelected() {
        Rule pending = rule("p", "Pending", "https://p.example.com", ResourceKind.OTHER, "u-comp",
                List.of(), null, null, 0, ResourceStatus.PENDING);
        Rule rejected = rule("x", "Rejected", "https://x.example.com", ResourceKind.OTHER, "u-comp",
                List.of(), null, null, 0, ResourceStatus.REJECTED);
        Rule ok = forUnit("ok", "Approved", "https://ok.example.com", "u-comp");

        assertThat(ids(UnitResourceLinkService.select(List.of(pending, rejected, ok), COMP1000)))
                .containsExactly("ok");
    }

    @Test
    void duplicateUrlsCollapseToTheMostSpecificRow() {
        Rule siteWide = approved("site", "Discord (site)", "https://discord.gg/comssa", ResourceKind.DISCORD,
                List.of(), null, null);
        Rule prefixed = approved("prefix", "Discord (COMP)", "https://discord.gg/comssa", ResourceKind.DISCORD,
                List.of("COMP"), null, null);
        Rule specific = forUnit("unit", "Discord (this unit)", "HTTPS://DISCORD.GG/comssa", "u-comp");

        List<UnitResourceLinkDTO> items = UnitResourceLinkService.select(List.of(siteWide, prefixed, specific), COMP1000);

        assertThat(ids(items)).containsExactly("unit");
        assertThat(items.get(0).scopeLabel()).isEqualTo("This unit");

        // On a unit the specific row does not cover, the prefixed rule wins over the site-wide one.
        Unit comp2000 = unit("u-comp2", "COMP2000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE);
        assertThat(ids(UnitResourceLinkService.select(List.of(siteWide, prefixed, specific), comp2000)))
                .containsExactly("prefix");
    }

    @Test
    void itemsAreGroupedByKindThenSortOrderThenTitle() {
        Rule notesB = rule("nb", "b notes", "https://nb.example.com", ResourceKind.NOTES, null, List.of(), null, null, 0, ResourceStatus.APPROVED);
        Rule notesA = rule("na", "A notes", "https://na.example.com", ResourceKind.NOTES, null, List.of(), null, null, 0, ResourceStatus.APPROVED);
        Rule notesFirst = rule("nf", "z notes pinned", "https://nf.example.com", ResourceKind.NOTES, null, List.of(), null, null, -5, ResourceStatus.APPROVED);
        Rule discord = rule("d", "Discord", "https://d.example.com", ResourceKind.DISCORD, null, List.of(), null, null, 99, ResourceStatus.APPROVED);
        Rule other = rule("o", "Other", "https://o.example.com", ResourceKind.OTHER, null, List.of(), null, null, -100, ResourceStatus.APPROVED);

        List<String> order = ids(UnitResourceLinkService.select(List.of(notesB, other, notesA, discord, notesFirst), COMP1000));

        assertThat(order).containsExactly("d", "nf", "na", "nb", "o");
    }

    @Test
    void ruleFromEntityCarriesTargetAndNormalisedPrefixes() {
        UnitResourceLink entity = new UnitResourceLink();
        entity.setId("e1");
        entity.setTitle("t");
        entity.setUrl("https://e.example.com");
        entity.setKind(ResourceKind.VIDEO);
        entity.setCodePrefixes(" comp, isad ,,ISAD ");
        entity.setStatus(ResourceStatus.APPROVED);

        Rule r = Rule.from(entity);

        assertThat(r.targetUnitId()).isNull();
        assertThat(r.prefixes()).containsExactly("COMP", "ISAD");
        assertThat(UnitResourceLinkService.select(List.of(r), COMP1000)).hasSize(1);
        assertThat(UnitResourceLinkService.select(List.of(r), MGMT1000)).isEmpty();
    }

    // ---------------------------------------------------------- scope labels

    @Test
    void scopeLabels() {
        assertThat(UnitResourceLinkService.scopeLabel(true, List.of("COMP"), Faculty.HUMANITIES, UnitLevel.POSTGRADUATE))
                .isEqualTo("This unit");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of(), null, null))
                .isEqualTo("All units");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of("COMP"), null, null))
                .isEqualTo("All COMP units");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of("COMP", "ISAD"), null, null))
                .isEqualTo("All COMP and ISAD units");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of("COMP", "ISAD", "ISEC"), null, null))
                .isEqualTo("All COMP, ISAD and ISEC units");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of("COMP", "ISAD", "ISEC", "CNCO", "CMPE"), null, null))
                .isEqualTo("All COMP, ISAD, ISEC and 2 more units");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of(), Faculty.SCIENCE_AND_ENGINEERING, null))
                .isEqualTo("Science and Engineering");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of(), null, UnitLevel.UNDERGRADUATE))
                .isEqualTo("Undergraduate units");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of(), Faculty.BUSINESS_AND_LAW, UnitLevel.POSTGRADUATE))
                .isEqualTo("Postgraduate Business and Law");
        assertThat(UnitResourceLinkService.scopeLabel(false, List.of("COMP"), Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE))
                .isEqualTo("All undergraduate COMP units in Science and Engineering");
    }

    @Test
    void publicDtoCarriesKindNameAndLabel() {
        Rule r = approved("r1", "ComSSA", "https://discord.gg/comssa", ResourceKind.DISCORD, List.of("COMP"), null, null);
        UnitResourceLinkDTO dto = UnitResourceLinkService.toPublicDTO(r);
        assertThat(dto.kind()).isEqualTo("DISCORD");
        assertThat(dto.kindLabel()).isEqualTo("Discord server");
        assertThat(dto.scopeLabel()).isEqualTo("All COMP units");
    }

    // ------------------------------------------------------ prefix parsing

    @Test
    void normalisePrefixesUpperCasesAndValidates() {
        assertThat(UnitResourceLinkService.normalisePrefixes(" comp , isad;cnco ")).containsExactly("COMP", "ISAD", "CNCO");
        assertThat(UnitResourceLinkService.normalisePrefixes(null)).isEmpty();
        assertThat(UnitResourceLinkService.normalisePrefixes("  ")).isEmpty();
        assertThatThrownBy(() -> UnitResourceLinkService.normalisePrefixes("COMP,C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitResourceLinkService.normalisePrefixes("COMP,IS-AD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseFacultyAcceptsNameOrDisplayName() {
        assertThat(UnitResourceLinkService.parseFaculty("science_and_engineering")).isEqualTo(Faculty.SCIENCE_AND_ENGINEERING);
        assertThat(UnitResourceLinkService.parseFaculty("Science and Engineering")).isEqualTo(Faculty.SCIENCE_AND_ENGINEERING);
        assertThat(UnitResourceLinkService.parseFaculty("")).isNull();
        assertThat(UnitResourceLinkService.parseLevel("postgraduate")).isEqualTo(UnitLevel.POSTGRADUATE);
        assertThatThrownBy(() -> UnitResourceLinkService.parseFaculty("Magic")).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- preview

    private static UnitResourceLinkRepo.UnitKey key(String code, Faculty f, UnitLevel l) {
        return new UnitResourceLinkRepo.UnitKey() {
            @Override public String getCode() { return code; }
            @Override public Faculty getFaculty() { return f; }
            @Override public UnitLevel getLevel() { return l; }
        };
    }

    @Test
    void previewCountsMatchingUnitsAndSamplesCodes() {
        when(repo.unitKeysForPreview()).thenReturn(List.of(
                key("COMP1000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE),
                key("COMP5000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.POSTGRADUATE),
                key("ISAD1000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE),
                key("MGMT1000", Faculty.BUSINESS_AND_LAW, UnitLevel.UNDERGRADUATE)));

        AdminUnitResourcePreviewDTO preview = service().preview("comp, isad", null, "UNDERGRADUATE", null);

        assertThat(preview.matchCount()).isEqualTo(2);
        assertThat(preview.sampleCodes()).containsExactly("COMP1000", "ISAD1000");
        assertThat(preview.scopeLabel()).isEqualTo("All undergraduate COMP and ISAD units");
    }

    @Test
    void previewWithUnitCodeChecksThatOneUnit() {
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));
        when(unitRepo.findByCode("NOPE9999")).thenReturn(Optional.empty());

        assertThat(service().preview(null, null, null, "comp1000").matchCount()).isEqualTo(1);
        assertThat(service().preview(null, null, null, "nope9999").matchCount()).isEqualTo(0);
    }

    // --------------------------------------------------------- suggestions

    private void signInAs(String email) {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(email, "pw", List.of())));
    }

    private User student() {
        User u = new User();
        u.setId("user-1");
        u.setEmail("alice@student.curtin.edu.au");
        return u;
    }

    @Test
    void suggestCreatesAPendingRowTargetingTheUnit() {
        signInAs("alice@student.curtin.edu.au");
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(student()));
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);
        when(repo.findByStatusOrderBySortOrderAscTitleAsc(ResourceStatus.APPROVED)).thenReturn(List.of());
        when(repo.findByStatusOrderByCreatedAtDesc(ResourceStatus.PENDING)).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(inv -> {
            UnitResourceLink saved = inv.getArgument(0);
            saved.setId("new-id");
            return saved;
        });

        UnitResourceLinkDTO dto = service().suggest("COMP1000", new UnitResourceLinkSuggestionRequest(
                "  Past papers archive ", "pastpapers.example.com/comp1000?y=2024", " Every year since 2019 ", "past_papers", null));

        ArgumentCaptor<UnitResourceLink> captor = ArgumentCaptor.forClass(UnitResourceLink.class);
        verify(repo).save(captor.capture());
        UnitResourceLink saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ResourceStatus.PENDING);
        assertThat(saved.getTargetUnit()).isSameAs(COMP1000);
        assertThat(saved.getSubmittedBy().getId()).isEqualTo("user-1");
        assertThat(saved.getTitle()).isEqualTo("Past papers archive");
        assertThat(saved.getUrl()).isEqualTo("https://pastpapers.example.com/comp1000?y=2024");
        assertThat(saved.getDescription()).isEqualTo("Every year since 2019");
        assertThat(saved.getKind()).isEqualTo(ResourceKind.PAST_PAPERS);
        assertThat(saved.getCodePrefixes()).isNull();
        assertThat(dto.scopeLabel()).isEqualTo("This unit");
    }

    @Test
    void suggestRejectsUnsafeUrlsBeforeTouchingTheDatabase() {
        signInAs("alice@student.curtin.edu.au");
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service().suggest("COMP1000", new UnitResourceLinkSuggestionRequest(
                "Bad", "javascript:alert(1)", null, "OTHER", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void suggestRejectsProfanityAndUnknownKinds() {
        signInAs("alice@student.curtin.edu.au");
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service().suggest("COMP1000", new UnitResourceLinkSuggestionRequest(
                "Fine", "https://ok.example.com", null, "PODCAST", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown resource kind");

        when(profanityFilterService.containsProfanity(anyString())).thenReturn(true);
        assertThatThrownBy(() -> service().suggest("COMP1000", new UnitResourceLinkSuggestionRequest(
                "Rude", "https://ok.example.com", null, "OTHER", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("community standards");

        verify(repo, never()).save(any());
    }

    @Test
    void suggestRejectsALinkAlreadyListedOnTheUnit() {
        signInAs("alice@student.curtin.edu.au");
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(student()));
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);

        UnitResourceLink existing = new UnitResourceLink();
        existing.setId("existing");
        existing.setTitle("ComSSA");
        existing.setUrl("https://discord.gg/comssa");
        existing.setKind(ResourceKind.DISCORD);
        existing.setCodePrefixes("COMP");
        existing.setStatus(ResourceStatus.APPROVED);
        when(repo.findByStatusOrderBySortOrderAscTitleAsc(ResourceStatus.APPROVED)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service().suggest("COMP1000", new UnitResourceLinkSuggestionRequest(
                "ComSSA again", "discord.gg/comssa", null, "DISCORD", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already listed");

        verify(repo, never()).save(any());
    }

    @Test
    void suggestRequiresASignedInUser() {
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));

        assertThatThrownBy(() -> service().suggest("COMP1000", new UnitResourceLinkSuggestionRequest(
                "x", "https://ok.example.com", null, "OTHER", null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
