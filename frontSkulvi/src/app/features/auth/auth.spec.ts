import { TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';
import { Auth } from './auth';
import { AuthService } from './auth';

describe('Auth', () => {
  let component: Auth;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['createUser']);

    TestBed.configureTestingModule({
      imports: [Auth],
      providers: [{ provide: AuthService, useValue: authService }],
    });

    component = TestBed.createComponent(Auth).componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should show a clear message when the email already exists', () => {
    authService.createUser.and.returnValue(
      throwError(() => ({ status: 409, message: 'Conflict' }))
    );

    component.userRequest = {
      firstName: 'Alice',
      lastName: 'Dupont',
      email: 'alice@example.com',
      password: 'secret123',
    };

    component.createUser();

    expect(component.isError).toBeTrue();
    expect(component.message).toBe('Cette adresse e-mail est déjà utilisée.');
  });
});
