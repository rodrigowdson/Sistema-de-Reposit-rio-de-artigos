import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Orientador, OrientadorRequest } from '../models/repositorio.models';

@Injectable({ providedIn: 'root' })
export class OrientadorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/orientadores';

  listar(apenasAtivos = false): Observable<Orientador[]> {
    const params = new HttpParams().set('apenasAtivos', apenasAtivos);
    return this.http.get<Orientador[]>(this.baseUrl, { params });
  }

  criar(dados: OrientadorRequest): Observable<Orientador> {
    return this.http.post<Orientador>(this.baseUrl, dados);
  }

  atualizar(id: number, dados: OrientadorRequest): Observable<Orientador> {
    return this.http.put<Orientador>(`${this.baseUrl}/${id}`, dados);
  }

  alterarAtivo(id: number, ativo: boolean): Observable<Orientador> {
    return this.http.patch<Orientador>(`${this.baseUrl}/${id}/ativo`, { ativo });
  }
}
