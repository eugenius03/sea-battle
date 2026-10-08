export interface UserResponse {
  id: string;
  username: string;
  email?: string | null;
  emailVerified?: boolean;
}

export type User = UserResponse;

export interface UserLoginRequest {
  username: string;
  password?: string;
}

export interface UserRegistrationRequest {
  username: string;
  password?: string;
  email?: string | null;
}
