package org.example.skulvi_cv.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.Utilisateur.AppUser;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.roles.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-email:}")
    private String adminEmail;

    @Value("${app.security.admin-password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD absents : aucun compte admin créé");
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setFirstName("Admin");
        admin.setLastName("Skulvi");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Roles.ADMIN);
        users.save(admin);
        log.info("Compte admin créé : {}", email);
    }
}