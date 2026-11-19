import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Curso, CursoRequest } from '../models/repositorio.models';

@Injectable({ providedIn: 'root' })
export class CursoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/cursos';

  listar(): Observable<Curso[]> {
    return this.http.get<Curso[]>(this.baseUrl);
  }

  criar(dados: CursoRequest): Observable<Curso> {
    return this.http.post<Curso>(this.baseUrl, dados);
  }

  atualizar(id: number, dados: CursoRequest): Observable<Curso> {
    return this.http.put<Curso>(`${this.baseUrl}/${id}`, dados);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
