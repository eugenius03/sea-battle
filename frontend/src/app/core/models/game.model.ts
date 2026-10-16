import { MatchStatus } from './match.model';
import { MoveResponse, MoveType } from './move.model';

export type Orientation = 'HORIZONTAL' | 'VERTICAL';

export type ShipType = 'SINGLE_DECK' | 'DOUBLE_DECK' | 'TRIPLE_DECK' | 'QUADRO_DECK';

export interface ShipDefinition {
  type: ShipType;
  name: string;
  size: number;
  allowedCount: number;
}

export const SHIP_DEFINITIONS: Record<ShipType, ShipDefinition> = {
  QUADRO_DECK: { type: 'QUADRO_DECK', name: 'Battleship', size: 4, allowedCount: 1 },
  TRIPLE_DECK: { type: 'TRIPLE_DECK', name: 'Cruiser', size: 3, allowedCount: 2 },
  DOUBLE_DECK: { type: 'DOUBLE_DECK', name: 'Destroyer', size: 2, allowedCount: 3 },
  SINGLE_DECK: { type: 'SINGLE_DECK', name: 'Patrol Boat', size: 1, allowedCount: 4 },
};

export interface ShipRequest {
  shipType: ShipType;
  startX: number;
  startY: number;
  orientation: Orientation;
}

export interface ShipResponse {
  id: number;
  shipType: ShipType;
  startX: number;
  startY: number;
  orientation: Orientation;
}

export interface GameInfoResponse {
  playerShips: ShipResponse[];
  playerMoves: MoveResponse[];
  opponentMoves: MoveResponse[];
  matchStatus: MatchStatus;
  matchPlayerId: string;
  droneUsage: Record<MoveType, number>;
  currentTurnPlayerId: string | null;
}

export interface FireResponse {
  moveResponses: MoveResponse[];
  currentTurnPlayerId: string | null;
}

export interface BoardCell {
  x: number;
  y: number;
  key: string;
  hasShip?: boolean;
  shipId?: number;
  isHit?: boolean;
  isMiss?: boolean;
  isSunk?: boolean;
  isSurveillance?: boolean;
  isInactive?: boolean;
  isDronePreview?: boolean;
  dronePreviewType?: 'SURVEILLANCE_DRONE' | 'ATTACK_DRONE';
  isDroneCenter?: boolean;
  isDragOver?: boolean;
  isPlacementValid?: boolean;
  isPlacementInvalid?: boolean;
  isRecentMove?: boolean;
}
