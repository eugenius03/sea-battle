import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { switchMap, timer } from 'rxjs';
import { AuthState } from '@core/state/auth.state';
import { safeReturnUrl } from '@core/utils/return-url.util';
import { IconComponent } from '@shared/components/icon/icon.component';

const RETRY_INTERVAL_MS = 3000;

@Component({
  selector: 'app-unavailable',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8 min-h-0">
      <div
        class="border-2 border-black shadow-[6px_6px_0px_#000] w-full max-w-sm p-6 sm:p-8 bg-white text-center"
      >
        <div
          class="w-16 h-14 bg-black text-white mx-auto mb-4 flex items-center justify-center shadow-[3px_3px_0px_#000]"
        >
          <app-icon name="pixel-ship" [size]="40" class="text-white"></app-icon>
        </div>
        <h1 class="text-xl font-extrabold uppercase tracking-tight text-black">
          Server unreachable
        </h1>
        <p class="text-xs font-semibold text-neutral-500 mt-2 uppercase tracking-wider">
          Your session is safe. Reconnecting automatically…
        </p>
      </div>
    </div>
  `,
})
export class UnavailableComponent implements OnInit {
  private readonly authState = inject(AuthState);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

    timer(RETRY_INTERVAL_MS, RETRY_INTERVAL_MS)
      .pipe(
        switchMap(() => this.authState.loadCurrentUser()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((user) => {
        if (user) {
          this.router.navigateByUrl(safeReturnUrl(returnUrl));
        } else if (!this.authState.backendUnavailable()) {
          this.router.navigate(['/auth'], { queryParams: { returnUrl } });
        }
      });
  }
}
