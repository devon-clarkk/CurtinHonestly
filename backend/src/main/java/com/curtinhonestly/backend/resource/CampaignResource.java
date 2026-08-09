package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.CampaignValidationDTO;
import com.curtinhonestly.backend.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignResource {

    private final CampaignService campaignService;

    @GetMapping("/validate")
    public ResponseEntity<CampaignValidationDTO> validateCampaign(
            @RequestParam(required = false) String ref,
            @RequestParam(required = false) String code) {
        return ResponseEntity.ok(campaignService.validateCampaign(ref, code));
    }

    // Records an attributed visit for a referral link. Fired by the app when a
    // visitor lands via a ?ref= link. Fire-and-forget: always returns 204 so a
    // tracking failure never disrupts the page.
    @PostMapping("/visit")
    public ResponseEntity<Void> recordVisit(@RequestParam String ref) {
        campaignService.recordVisit(ref);
        return ResponseEntity.noContent().build();
    }
}
