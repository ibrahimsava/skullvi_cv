import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../auth';
import { UserRequest } from '../model';

@Component({
  imports: [FormsModule, RouterLink],
  selector: 'app-auth',
  styleUrl: './auth.scss',
  templateUrl: './auth.html',
})
export class Auth implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  mode: 'login' | 'register' = 'register';

  userRequest: UserRequest = {
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  };

  ngOnInit(): void {
    const currentRoute = this.route.snapshot.url[0]?.path ?? 'register';
    this.mode = currentRoute === 'login' ? 'login' : 'register';
  }

  get isLogin(): boolean {
    return this.mode === 'login';
  }

  submit(): void {
    if (this.mode === 'login') {
      this.authService.login(this.userRequest.email, this.userRequest.password).subscribe((response) => {
        console.log('Connexion réussie :', response);
      });
      return;
    }

    this.createUser();
  }

  createUser(): void {
    this.authService.createUser(this.userRequest).subscribe((user) => {
      console.log('Utilisateur créé :', user);
    });
  }
}


