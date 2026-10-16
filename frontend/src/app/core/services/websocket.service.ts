import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject } from 'rxjs';
import { environment } from '@env/environment';
import { MatchStatusMessage, MoveMessage, PresenceMessage } from '@core/models/ws.model';
import { MatchmakingStatusResponse } from '@core/models/match.model';

interface ActiveSubscriptionEntry {
  destination: string;
  callback: (message: IMessage) => void;
  stompSub: StompSubscription | null;
}

@Injectable({
  providedIn: 'root',
})
export class WebSocketService {
  private client: Client | null = null;
  readonly isConnected = signal<boolean>(false);
  readonly reconnected$ = new Subject<void>();

  private currentHeaders: Record<string, string> | null = null;
  private connectionPromise: Promise<void> | null = null;
  private hasInitiallyConnected = false;

  private activeSubscriptions = new Map<number, ActiveSubscriptionEntry>();
  private nextSubId = 1;

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

          // Resubscribe all active subscriptions on every connect / reconnect
          for (const sub of this.activeSubscriptions.values()) {
            if (sub.stompSub) {
              try {
                sub.stompSub.unsubscribe();
              } catch {}
            }
            sub.stompSub = this.client!.subscribe(sub.destination, sub.callback);
          }

          if (this.hasInitiallyConnected) {
            this.reconnected$.next();
          } else {
            this.hasInitiallyConnected = true;
          }

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
      this.hasInitiallyConnected = false;
      for (const sub of this.activeSubscriptions.values()) {
        sub.stompSub = null;
      }
    }
  }

  private subscribeToTopic<T>(
    destination: string,
    headers: Record<string, string> = {},
  ): Observable<T> {
    return new Observable<T>((observer) => {
      const subId = this.nextSubId++;
      let isUnsubscribed = false;

      const callback = (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body) as T;
          observer.next(payload);
        } catch (e) {
          console.error(`Failed to parse WebSocket message from ${destination}:`, e);
        }
      };

      const entry: ActiveSubscriptionEntry = {
        destination,
        callback,
        stompSub: null,
      };
      this.activeSubscriptions.set(subId, entry);

      this.connect(headers)
        .then(() => {
          if (isUnsubscribed || !this.client || !this.client.connected) {
            return;
          }

          if (!entry.stompSub) {
            entry.stompSub = this.client.subscribe(destination, callback);
          }
        })
        .catch((err) => {
          console.warn('WebSocket connection not established:', err);
          observer.error(err);
        });

      return () => {
        isUnsubscribed = true;
        const sub = this.activeSubscriptions.get(subId);
        if (sub?.stompSub) {
          try {
            sub.stompSub.unsubscribe();
          } catch {}
        }
        this.activeSubscriptions.delete(subId);
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
