import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Usuario } from '../models/repositorio.models';

const TOKEN_KEY = 'repositorio_jwt_token';
const USER_KEY = 'repositorio_usuario';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly _usuario = signal<Usuario | null>(this.carregarUsuarioLocal());

  readonly token = this._token.asReadonly();
  readonly usuario = this._usuario.asReadonly();
  readonly estaAutenticado = computed(() => !!this._token() && !!this._usuario());
  readonly podeModerar = computed(() => {
    const perfil = this._usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'BIBLIOTECARIO';
  });
  readonly ehAdmin = computed(() => this._usuario()?.perfil === 'ADMIN');

  constructor() {
    if (this._token()) {
      this.http.get<Usuario>('/api/auth/me').subscribe({
        next: (u) => {
          this.salvarUsuario(u);
          const url = this.router.url;
          if (url.startsWith('/admin/usuarios') || url.startsWith('/admin/backups')) {
            if (!this.ehAdmin()) this.router.navigate(['/']);
          } else if (url.startsWith('/admin') && !this.podeModerar()) {
            this.router.navigate(['/']);
          }
        },
        error: () => this.limparSessao(),
      });
    }
  }

  login(email: string, senha: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, senha })
      .pipe(tap((res) => this.salvarSessao(res)));
  }

  registrar(nome: string, email: string, senha: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/registrar', { nome, email, senha })
      .pipe(tap((res) => this.salvarSessao(res)));
  }

  sair(): void {
    this.limparSessao();
    this.router.navigate(['/']);
  }

  limparSessao(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._token.set(null);
    this._usuario.set(null);
  }

  private salvarSessao(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    this._token.set(res.token);
    this.salvarUsuario(res.usuario);
  }

  private salvarUsuario(usuario: Usuario): void {
    localStorage.setItem(USER_KEY, JSON.stringify(usuario));
    this._usuario.set(usuario);
  }

  private carregarUsuarioLocal(): Usuario | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as Usuario;
    } catch {
      return null;
    }
  }
}
