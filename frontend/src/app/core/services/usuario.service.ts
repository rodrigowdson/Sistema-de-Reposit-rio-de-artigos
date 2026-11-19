import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Perfil, Usuario } from '../models/repositorio.models';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/usuarios';

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.baseUrl);
  }

  alterarPerfil(id: number, perfil: Perfil): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.baseUrl}/${id}/perfil`, { perfil });
  }

  alterarAtivo(id: number, ativo: boolean): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.baseUrl}/${id}/ativo`, { ativo });
  }
}
