import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { UserLoginRequest, UserRegistrationRequest, UserResponse } from '@core/models/auth.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  getCurrentUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/me`, {
      withCredentials: true,
    });
  }

  login(credentials: UserLoginRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/login`, credentials, {
      withCredentials: true,
    });
  }

  register(credentials: UserRegistrationRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/register`, credentials, {
      withCredentials: true,
    });
  }

  guestLogin(nickname: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/guest`, nickname, {
      headers: { 'Content-Type': 'text/plain' },
      withCredentials: true,
    });
  }

  logout(): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/logout`,
      {},
      {
        withCredentials: true,
      },
    );
  }

  refresh(): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/refresh`,
      {},
      {
        withCredentials: true,
      },
    );
  }
}
