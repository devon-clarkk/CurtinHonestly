package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.AdminNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminNotificationSettingsRepo extends JpaRepository<AdminNotificationSettings, String> {
}
