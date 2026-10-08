import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { MatchResponse } from '@core/models/match.model';

@Injectable({
  providedIn: 'root',
})
export class MatchService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/match`;

  createMatch(): Observable<MatchResponse> {
    return this.http.post<MatchResponse>(`${this.baseUrl}/create`, {}, { withCredentials: true });
  }

  joinMatch(inviteToken: string): Observable<MatchResponse> {
    return this.http.post<MatchResponse>(
      `${this.baseUrl}/join`,
      {},
      {
        params: { inviteToken },
        withCredentials: true,
      },
    );
  }

  rematch(inviteToken: string): Observable<MatchResponse> {
    return this.http.post<MatchResponse>(
      `${this.baseUrl}/${inviteToken}/rematch`,
      {},
      { withCredentials: true },
    );
  }

  findGame(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/find`, {}, { withCredentials: true });
  }
}
