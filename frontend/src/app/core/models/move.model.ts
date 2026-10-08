export type MoveType = 'STANDARD' | 'SURVEILLANCE_DRONE' | 'ATTACK_DRONE';

export type MoveResult = 'MISS' | 'SURVEILLANCE' | 'HIT' | 'SUNK' | 'FINISHED';

export interface MoveRequest {
  x: number;
  y: number;
  moveType: MoveType;
}

export interface MoveResponse {
  targetX: number;
  targetY: number;
  moveResult: MoveResult;
}
