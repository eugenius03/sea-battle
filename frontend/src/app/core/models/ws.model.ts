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
  inviteToken?: string;
}

export interface MoveMessage {
  moveResponses: MoveResponse[];
  currentTurnPlayerId: string | null;
}

export interface MatchStatusMessage {
  inviteToken: string;
  matchStatus: MatchStatus;
  currentTurnPlayerId: string;
  winnerShips?: ShipResponse[];
}
