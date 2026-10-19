import { ShipResponse } from './game.model';

export type MatchStatus = 'WAITING' | 'PLANNING' | 'IN_PROGRESS' | 'FINISHED';
export type MatchResult = 'WON' | 'LOST';

export interface MatchInviteResponse {
  inviteToken: string;
  playerId: string;
}

export type MatchResponse = MatchInviteResponse;

export interface MatchHistoryResponse {
  id: string;
  result: MatchResult;
  playedAt: string;
  durationSeconds: number;
}

export interface MatchParticipantResponse {
  playerId: string;
  userId: string;
  username: string;
  isWinner: boolean;
  ships: ShipResponse[];
}

export interface ReplayMoveResponse {
  shooterId: string;
  targetX: number;
  targetY: number;
  moveType: string;
  moveResult: string;
  isMoveOrigin?: boolean;
}

export interface MatchDetailsResponse {
  inviteToken: string;
  status: MatchStatus;
  players: MatchParticipantResponse[];
  moves: ReplayMoveResponse[];
  createdAt: string;
  finishedAt: string;
}

export interface MatchmakingStatusResponse {
  queuePosition?: number | null;
  inviteToken?: string | null;
}
