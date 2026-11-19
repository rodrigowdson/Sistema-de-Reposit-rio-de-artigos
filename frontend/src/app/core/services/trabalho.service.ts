import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Page,
  StatusTrabalho,
  Trabalho,
  TrabalhoFiltro,
  TrabalhoRequest,
} from '../models/repositorio.models';

@Injectable({ providedIn: 'root' })
export class TrabalhoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/trabalhos';

  buscar(filtro: TrabalhoFiltro, page = 0, size = 10, sort = 'ano,desc'): Observable<Page<Trabalho>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filtro.cursoId) params = params.set('cursoId', filtro.cursoId);
    if (filtro.orientadorId) params = params.set('orientadorId', filtro.orientadorId);
    if (filtro.ano) params = params.set('ano', filtro.ano);
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.termo && filtro.termo.trim()) params = params.set('termo', filtro.termo.trim());

    return this.http.get<Page<Trabalho>>(this.baseUrl, { params });
  }

  listarMeus(page = 0, size = 10): Observable<Page<Trabalho>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'criadoEm,desc');
    return this.http.get<Page<Trabalho>>(`${this.baseUrl}/meus`, { params });
  }

  buscarPorId(id: number): Observable<Trabalho> {
    return this.http.get<Trabalho>(`${this.baseUrl}/${id}`);
  }

  listarAnos(): Observable<number[]> {
    return this.http.get<number[]>(`${this.baseUrl}/anos`);
  }

  enviar(dados: TrabalhoRequest, arquivo: File): Observable<HttpEvent<Trabalho>> {
    const formData = new FormData();
    formData.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }));
    formData.append('arquivo', arquivo, arquivo.name);

    return this.http.post<Trabalho>(this.baseUrl, formData, {
      reportProgress: true,
      observe: 'events',
    });
  }

  atualizar(id: number, dados: TrabalhoRequest): Observable<Trabalho> {
    return this.http.put<Trabalho>(`${this.baseUrl}/${id}`, dados);
  }

  alterarStatus(id: number, status: StatusTrabalho): Observable<Trabalho> {
    return this.http.patch<Trabalho>(`${this.baseUrl}/${id}/status`, { status });
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  obterPdfBlob(id: number, inline = false): Observable<Blob> {
    const params = new HttpParams().set('inline', inline);
    return this.http.get(`${this.baseUrl}/${id}/download`, {
      params,
      responseType: 'blob',
    });
  }
}
