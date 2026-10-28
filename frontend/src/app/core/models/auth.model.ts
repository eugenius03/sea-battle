export interface PlayerCombatStats {
  matchesPlayed: number;
  wins: number;
  losses: number;
  winRate: number;
  shotsFired: number;
  hits: number;
  misses: number;
  accuracy: number;
  currentWinStreak: number;
}

export type UserType = 'REGISTERED' | 'GUEST' | 'BOT';

export interface UserResponse {
  id: string;
  username: string;
  email?: string | null;
  emailVerified?: boolean;
  memberSince?: string;
  userType?: UserType;
  stats?: PlayerCombatStats;
}

export interface PublicUserResponse {
  username: string;
  memberSince?: string;
  userType?: UserType;
  stats?: PlayerCombatStats;
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
