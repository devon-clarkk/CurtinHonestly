package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.security.AppUserDetails;
import com.curtinhonestly.backend.util.EmailNormalizer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = EmailNormalizer.normalize(email);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + normalizedEmail));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name())) // This gives "ROLE_USER", "ROLE_ADMIN"
                .collect(Collectors.toList());

        // AppUserDetails (a subclass of Spring's User) carries tokensValidAfter so the
        // JWT filter can reject sessions older than the last credential change without
        // a second query per request.
        return new AppUserDetails(
                user.getEmail(),
                user.getPassword(),
                !user.isBanned(),
                authorities,
                user.getTokensValidAfter()
        );
    }
}

