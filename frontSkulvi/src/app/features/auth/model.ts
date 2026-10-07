

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

 export interface UserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

