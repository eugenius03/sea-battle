import { Injectable, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import { MatchStatusMessage, MoveMessage, PresenceMessage } from '@core/models/ws.model';
import { MatchmakingStatusResponse } from '@core/models/match.model';

@Injectable({
  providedIn: 'root',
})
export class WebSocketService {
  private client: Client | null = null;
  readonly isConnected = signal<boolean>(false);
  private currentHeaders: Record<string, string> | null = null;
  private connectionPromise: Promise<void> | null = null;

  connect(headers: Record<string, string> = {}): Promise<void> {
    if (this.client?.connected && this.isSameHeaders(headers)) {
      return Promise.resolve();
    }

    if (this.connectionPromise && this.isSameHeaders(headers)) {
      return this.connectionPromise;
    }

    if (this.client) {
      this.disconnect();
    }

    this.currentHeaders = { ...headers };

    this.connectionPromise = new Promise((resolve, reject) => {
      this.client = new Client({
        webSocketFactory: () => new SockJS(environment.wsUrl),
        connectHeaders: headers,
        reconnectDelay: 3000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          this.isConnected.set(true);
          resolve();
        },
        onDisconnect: () => {
          this.isConnected.set(false);
        },
        onStompError: (frame) => {
          console.error('STOMP protocol error:', frame.headers['message']);
          this.isConnected.set(false);
          this.connectionPromise = null;
          reject(frame);
        },
        onWebSocketClose: () => {
          this.isConnected.set(false);
          this.connectionPromise = null;
        },
      });

      this.client.activate();
    });

    return this.connectionPromise;
  }

  private isSameHeaders(headers: Record<string, string>): boolean {
    if (!this.currentHeaders) return false;
    const currentKeys = Object.keys(this.currentHeaders);
    const newKeys = Object.keys(headers);
    if (currentKeys.length !== newKeys.length) return false;
    return currentKeys.every((k) => this.currentHeaders![k] === headers[k]);
  }

  disconnect(): void {
    if (this.client) {
      try {
        this.client.deactivate();
      } catch {}
      this.client = null;
      this.isConnected.set(false);
      this.connectionPromise = null;
      this.currentHeaders = null;
    }
  }

  private subscribeToTopic<T>(
    destination: string,
    headers: Record<string, string> = {},
  ): Observable<T> {
    return new Observable<T>((observer) => {
      let stompSub: { unsubscribe: () => void } | null = null;
      let isUnsubscribed = false;

      this.connect(headers)
        .then(() => {
          if (isUnsubscribed || !this.client || !this.client.connected) {
            return;
          }

          stompSub = this.client.subscribe(destination, (message: IMessage) => {
            try {
              const payload = JSON.parse(message.body) as T;
              observer.next(payload);
            } catch (e) {
              console.error(`Failed to parse WebSocket message from ${destination}:`, e);
            }
          });
        })
        .catch((err) => {
          console.warn('WebSocket connection not established:', err);
          observer.error(err);
        });

      return () => {
        isUnsubscribed = true;
        if (stompSub) {
          try {
            stompSub.unsubscribe();
          } catch {}
        }
      };
    });
  }

  watchMatchPresence(inviteToken: string, matchPlayerId: string): Observable<PresenceMessage> {
    return this.subscribeToTopic<PresenceMessage>(`/topic/match/${inviteToken}/presence`, {
      'X-Invite-Token': inviteToken,
      'X-Match-Player-Id': matchPlayerId,
    });
  }

  watchMatchStatus(inviteToken: string, matchPlayerId: string): Observable<MatchStatusMessage> {
    return this.subscribeToTopic<MatchStatusMessage>(`/topic/match/${inviteToken}/status`, {
      'X-Invite-Token': inviteToken,
      'X-Match-Player-Id': matchPlayerId,
    });
  }

  watchMatchMoves(inviteToken: string, matchPlayerId: string): Observable<MoveMessage> {
    return this.subscribeToTopic<MoveMessage>(`/user/queue/match/${inviteToken}`, {
      'X-Invite-Token': inviteToken,
      'X-Match-Player-Id': matchPlayerId,
    });
  }

  watchMatchmaking(userId: string): Observable<MatchmakingStatusResponse> {
    return this.subscribeToTopic<MatchmakingStatusResponse>(`/topic/matchmaking/${userId}`, {
      'X-User-Id': userId,
    });
  }
}
