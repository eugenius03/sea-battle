import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { MatchDetailsResponse, MatchHistoryResponse, MatchResponse } from '@core/models/match.model';

@Injectable({
  providedIn: 'root',
})
export class MatchService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/match`;

  createMatch(): Observable<MatchResponse> {
    return this.http.post<MatchResponse>(`${this.baseUrl}/create`, {}, { withCredentials: true });
  }

  createBotMatch(): Observable<MatchResponse> {
    return this.http.post<MatchResponse>(`${this.baseUrl}/bot`, {}, { withCredentials: true });
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

  getMatchHistory(username: string, page = 0, size = 10): Observable<MatchHistoryResponse[]> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<MatchHistoryResponse[]>(
      `${this.baseUrl}/history/${encodeURIComponent(username)}`,
      {
        params,
        withCredentials: true,
      },
    );
  }

  getMatch(inviteToken: string): Observable<MatchDetailsResponse> {
    return this.http.get<MatchDetailsResponse>(
      `${this.baseUrl}/${encodeURIComponent(inviteToken)}`,
      {
        withCredentials: true,
      },
    );
  }
}
