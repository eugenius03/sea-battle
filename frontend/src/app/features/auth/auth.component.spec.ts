import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthComponent } from './auth.component';
import { AuthState } from '@core/state/auth.state';

describe('AuthComponent', () => {
  let component: AuthComponent;
  let fixture: ComponentFixture<AuthComponent>;
  let mockAuthState: Partial<AuthState>;

  beforeEach(async () => {
    mockAuthState = {
      guestLogin: vi.fn().mockReturnValue(of({ id: 'guest-1', username: 'Iron Corsair' })),
      login: vi.fn().mockReturnValue(of({ id: 'user-1', username: 'vas' })),
      register: vi.fn().mockReturnValue(of({ id: 'user-2', username: 'newuser' })),
    };

    await TestBed.configureTestingModule({
      imports: [AuthComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideRouter([{ path: 'lobby', component: class {} }]),
        { provide: AuthState, useValue: mockAuthState },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the auth component', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with a generated guest nickname without numbers', () => {
    expect(component.guestNickname).toBeTruthy();
    expect(component.guestNickname).not.toMatch(/\d/);
  });

  it('should allow rerolling guest nickname without producing numbers', () => {
    const original = component.guestNickname;
    component.rerollGuestNickname();
    expect(component.guestNickname).toBeTruthy();
    expect(component.guestNickname).not.toMatch(/\d/);
  });

  it('should initialize with underscore separator in nickname', () => {
    expect(component.guestNickname).toContain('_');
  });

  it('should switch mode between login and register', () => {
    component.onModeChange('register');
    expect(component.mode()).toBe('register');
  });

  it('should submit guest login with the current nickname', () => {
    component.guestNickname = 'Brave_Admiral';
    component.onGuestSubmit();

    expect(mockAuthState.guestLogin).toHaveBeenCalledWith('Brave_Admiral');
  });

  it('should validate optional email correctly', () => {
    component.onModeChange('register');

    // Empty email is valid because it is optional
    component.email = '';
    expect(component.isEmailInvalid).toBe(false);

    component.email = '   ';
    expect(component.isEmailInvalid).toBe(false);

    // Invalid format
    component.email = 'not-an-email';
    expect(component.isEmailInvalid).toBe(true);

    component.email = 'user@domain';
    expect(component.isEmailInvalid).toBe(true);

    // Valid format
    component.email = 'commander@navy.mil';
    expect(component.isEmailInvalid).toBe(false);
  });

  it('should submit registration with optional email when provided', () => {
    component.onModeChange('register');
    component.username = 'CaptainJack';
    component.password = 'secret123';
    component.email = 'jack@sparrow.com';

    component.onSubmit();

    expect(mockAuthState.register).toHaveBeenCalledWith({
      username: 'CaptainJack',
      password: 'secret123',
      email: 'jack@sparrow.com',
    });
  });

  it('should submit registration without email when email is blank', () => {
    component.onModeChange('register');
    component.username = 'CaptainJack';
    component.password = 'secret123';
    component.email = '   ';

    component.onSubmit();

    expect(mockAuthState.register).toHaveBeenCalledWith({
      username: 'CaptainJack',
      password: 'secret123',
    });
  });

  it('should prevent submission when email is invalid in register mode', () => {
    component.onModeChange('register');
    component.username = 'CaptainJack';
    component.password = 'secret123';
    component.email = 'invalid-email';

    component.onSubmit();

    expect(mockAuthState.register).not.toHaveBeenCalled();
  });
});
