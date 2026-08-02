package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.UnitRequest;
import com.curtinhonestly.backend.repo.UnitRequestRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitRequestServiceTest {

    @Mock UnitRequestRepo unitRequestRepo;

    @Captor ArgumentCaptor<UnitRequest> requestCaptor;

    private UnitRequestService service() {
        return new UnitRequestService(unitRequestRepo);
    }

    @Test
    void create_savesTrimmedCodeAndNote() {
        when(unitRequestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().create("  ISYS2000  ", "  Not in the catalog yet.  ");

        verify(unitRequestRepo).save(requestCaptor.capture());
        UnitRequest saved = requestCaptor.getValue();
        assertThat(saved.getRequestedCode()).isEqualTo("ISYS2000");
        assertThat(saved.getNote()).isEqualTo("Not in the catalog yet.");
    }

    @Test
    void create_treatsBlankNoteAsNull() {
        when(unitRequestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().create("ISYS2000", "   ");

        verify(unitRequestRepo).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getNote()).isNull();
    }

    @Test
    void create_acceptsNullNote() {
        when(unitRequestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().create("ISYS2000", null);

        verify(unitRequestRepo).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getNote()).isNull();
    }

    @Test
    void create_rejectsBlankCode() {
        assertThatThrownBy(() -> service().create("   ", "note"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(unitRequestRepo, never()).save(any());
    }

    @Test
    void create_rejectsCodeOverMaxLength() {
        assertThatThrownBy(() -> service().create("x".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(unitRequestRepo, never()).save(any());
    }

    @Test
    void create_rejectsNoteOverMaxLength() {
        assertThatThrownBy(() -> service().create("ISYS2000", "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(unitRequestRepo, never()).save(any());
    }

    @Test
    void getAll_returnsNewestFirstFromRepo() {
        UnitRequest r = new UnitRequest();
        when(unitRequestRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r));

        assertThat(service().getAll()).containsExactly(r);
    }

    @Test
    void delete_delegatesToRepo() {
        service().delete("req-1");
        verify(unitRequestRepo).deleteById("req-1");
    }
}
