import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BackupConfigRequest,
  BackupExecucao,
  BackupStatus,
} from '../models/repositorio.models';

@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/backups';

  obterStatus(): Observable<BackupStatus> {
    return this.http.get<BackupStatus>(`${this.baseUrl}/status`);
  }

  listarHistorico(): Observable<BackupExecucao[]> {
    return this.http.get<BackupExecucao[]>(`${this.baseUrl}/historico`);
  }

  executarAgora(): Observable<BackupExecucao> {
    return this.http.post<BackupExecucao>(`${this.baseUrl}/executar`, {});
  }

  restaurar(): Observable<BackupExecucao> {
    return this.http.post<BackupExecucao>(`${this.baseUrl}/restaurar`, {});
  }

  atualizarConfig(dados: BackupConfigRequest): Observable<BackupStatus> {
    return this.http.put<BackupStatus>(`${this.baseUrl}/config`, dados);
  }
}
