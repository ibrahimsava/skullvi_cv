/** Rôle global du compte. */
export type GlobalRole = 'CANDIDAT' | 'RECRUTEUR' | 'ADMIN';

/** Rôle à l'intérieur d'une organisation. */
export type OrgRole = 'OWNER' | 'RECRUITER' | 'VIEWER';

export type PlanCode = 'FREE' | 'STARTER' | 'PRO' | 'BUSINESS';

export type OrgStatus = 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

/**
 * Identité du compte connecté.
 *
 * <p>`organizationId` est nul pour un candidat : c'est ce qui distingue les deux espaces, sans
 * avoir à deviner depuis le rôle.
 */
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: GlobalRole;
  organizationId: string | null;
  organizationName: string | null;
  orgRole: OrgRole | null;
  plan: PlanCode | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

/** Inscription d'un candidat. */
export interface UserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

/** Création d'un espace recruteur : le compte et son organisation d'un coup. */
export interface OrgSignupRequest {
  organizationName: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}
