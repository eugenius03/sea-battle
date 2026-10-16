import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { FireResponse, GameInfoResponse, Orientation, ShipRequest, ShipResponse } from '@core/models/game.model';
import { MoveRequest } from '@core/models/move.model';

@Injectable({
  providedIn: 'root',
})
export class GameService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/game`;

  placeShip(inviteToken: string, request: ShipRequest): Observable<number> {
    return this.http.post<number>(`${this.baseUrl}/${inviteToken}/place-ship`, request, {
      withCredentials: true,
    });
  }

  generateRandomShips(inviteToken: string): Observable<ShipResponse[]> {
    return this.http.post<ShipResponse[]>(
      `${this.baseUrl}/${inviteToken}/generate-random-ships`,
      {},
      { withCredentials: true },
    );
  }

  moveShip(
    inviteToken: string,
    shipId: number,
    startX: number,
    startY: number,
    orientation: Orientation,
  ): Observable<number> {
    return this.http.post<number>(
      `${this.baseUrl}/${inviteToken}/move-ship/${shipId}`,
      {},
      {
        params: {
          startX: startX.toString(),
          startY: startY.toString(),
          orientation,
        },
        withCredentials: true,
      },
    );
  }

  markReady(inviteToken: string): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/${inviteToken}/mark-ready`,
      {},
      { withCredentials: true },
    );
  }

  fire(inviteToken: string, moveRequest: MoveRequest): Observable<FireResponse> {
    return this.http.post<FireResponse>(`${this.baseUrl}/${inviteToken}/fire`, moveRequest, {
      withCredentials: true,
    });
  }

  getGameInfo(inviteToken: string): Observable<GameInfoResponse> {
    return this.http.get<GameInfoResponse>(`${this.baseUrl}/${inviteToken}/info`, {
      withCredentials: true,
    });
  }
}
