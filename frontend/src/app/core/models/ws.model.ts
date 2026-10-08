import { MatchStatus } from './match.model';
import { MoveResponse } from './move.model';
import { ShipResponse } from './game.model';

export type PresenceEventType =
  | 'OPPONENT_CONNECTED'
  | 'DISCONNECTED'
  | 'OPPONENT_READY'
  | 'RECONNECTED'
  | 'REMATCH_REQUESTED'
  | 'REMATCH_AGREED';

export interface PresenceMessage {
  matchPlayerId?: string;
  presenceEventType: PresenceEventType;
  at: string;
  inviteToken?: string;
}

export interface MoveMessage {
  recipientMatchPlayerId: string;
  moveResponses: MoveResponse[];
  at: string;
  isItMyTurn: boolean;
}

export interface MatchStatusMessage {
  inviteToken: string;
  matchStatus: MatchStatus;
  at: string;
  currentTurnPlayerId: string;
  winnerShips?: ShipResponse[];
}
