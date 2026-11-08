package org.example.skulvi_cv.org;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrgDtos {

    private OrgDtos() {}

    /** Création d'un espace recruteur : le compte du propriétaire et son organisation d'un coup. */
    public record SignupRequest(
            @NotBlank @Size(max = 80) String organizationName,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record OrganizationResponse(
            UUID id,
            String name,
            String slug,
            Plan plan,
            String planLabel,
            OrgStatus status,
            boolean paid,
            /** Fin de la période déjà réglée. Null sur le plan gratuit. */
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            boolean writable,
            Instant createdAt) {}

    public record MemberResponse(
            UUID membershipId,
            UUID userId,
            String email,
            String firstName,
            String lastName,
            MemberRole role,
            boolean me,
            Instant joinedAt) {}

    public record MemberList(List<MemberResponse> members, int seats, int seatsLimit) {}

    /** Ajout d'un collègue. Le mot de passe initial lui est communiqué par le propriétaire. */
    public record InviteRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull MemberRole role) {}

    public record RoleUpdateRequest(@NotNull MemberRole role) {}

    public record OrganizationUpdateRequest(@NotBlank @Size(max = 80) String name) {}
}
