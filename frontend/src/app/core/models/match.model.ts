export type MatchStatus = 'WAITING' | 'PLANNING' | 'IN_PROGRESS' | 'FINISHED';

export interface MatchResponse {
  inviteToken: string;
  playerId: string;
}

export interface MatchmakingStatusResponse {
  queuePosition?: number | null;
  inviteToken?: string | null;
}
