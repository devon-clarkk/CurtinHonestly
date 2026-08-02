package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.dto.TipDTO;

import java.util.List;

public class TipMapper {

    public static TipDTO mapToDTO(UnitTip tip, String currentUserId) {
        TipDTO dto = new TipDTO();
        dto.setId(tip.getId());
        dto.setText(tip.getText());
        dto.setCreatedAt(tip.getCreatedAt());
        if (tip.getUser() != null) {
            dto.setAuthorVerified(tip.getUser().isVerifiedStudent());
            dto.setOwnedByCurrentUser(currentUserId != null && currentUserId.equals(tip.getUser().getId()));
        }
        return dto;
    }

    public static List<TipDTO> mapToDTOs(List<UnitTip> tips, String currentUserId) {
        return tips.stream().map(tip -> mapToDTO(tip, currentUserId)).toList();
    }
}
