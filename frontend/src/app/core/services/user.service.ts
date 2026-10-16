import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { PublicUserResponse, UserResponse } from '@core/models/auth.model';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/user`;

  getMe(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/me`, { withCredentials: true });
  }

  getPublicProfile(username: string): Observable<PublicUserResponse> {
    return this.http.get<PublicUserResponse>(`${this.baseUrl}/${encodeURIComponent(username)}`, {
      withCredentials: true,
    });
  }
}
