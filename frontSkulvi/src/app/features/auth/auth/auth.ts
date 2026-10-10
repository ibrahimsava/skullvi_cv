import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth';
import { UserRequest } from '../model';
import  { Router } from '@angular/router';
import { jwtDecode } from 'jwt-decode';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  selector: 'app-auth',
  styleUrl: './auth.scss',
  templateUrl: './auth.html',
})
export class Auth implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router); 

  mode: 'login' | 'register' = 'register';

  userRequest: UserRequest = {
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  };

  // UI error state for tests and user feedback
  isError = false;
  message = '';

  ngOnInit(): void {
    const url = this.router.url ?? '';
    if (url.includes('/login')) {
      this.mode = 'login';
    } else if (url.includes('/register')) {
      this.mode = 'register';
    } else {
      const path = this.route.snapshot.routeConfig?.path ?? '';
      this.mode = path === 'login' ? 'login' : path === 'register' ? 'register' : this.mode;
    }
  }

  get isLogin(): boolean {
    return this.mode === 'login';
  }

  submit(): void {
    // reset UI error state
    this.isError = false;
    this.message = '';

    if (this.mode === 'login') {
      this.authService.login(this.userRequest.email, this.userRequest.password).subscribe({
        next: (response) => {
            console.log('Connexion réussie :', response);
            // store access token so interceptor can send it on subsequent requests
            try {
              if (response?.accessToken) {
                localStorage.setItem('accessToken', response.accessToken);
              }
              // optionally store user info
              if (response?.user) {
                localStorage.setItem('user', JSON.stringify(response.user));
              }
               console.log('JWT décodé :', jwtDecode(response.accessToken));
               console.log('role lu :', this.authService.getUserRoles());

              const isAdmin = this.authService.isAdmin();

              if (isAdmin) {
                this.router.navigate(['/admin/dashboard']);
              } else {
                this.router.navigate(['/offres']);
              }

            } catch (err) {
              console.warn('Unable to store token in localStorage', err);
            }

           //  const user = response?.user;

        //  if (user?.role === 'ADMIN' || user?.role?.includes('ADMIN')) {
             //  this.router.navigate(['/creationoffre']);
                //  } else {
             //  this.router.navigate(['/offres']);
            //}
          },
        error: (err) => {
          console.error('Erreur connexion :', err);
          this.isError = true;
          this.message = err?.status === 401 ? 'Identifiants invalides.' : 'Erreur lors de la connexion.';
        }
      });
      return;
    }

    this.createUser();
}

  createUser(): void {
    // reset UI error state
    this.isError = false;
    this.message = '';

    this.authService.createUser(this.userRequest).subscribe({
      next: (user) => {
        console.log('Utilisateur créé :', user);
        this.router.navigate(['offres']);
      },
      error: (err) => {
        console.error('Erreur création :', err);
        if (err?.status === 409) {
          this.isError = true;
          this.message = 'Cette adresse e-mail est déjà utilisée.';
        } else {
          this.isError = true;
          this.message = 'Erreur lors de la création du compte.';
        }
      }
    });
  }


}


