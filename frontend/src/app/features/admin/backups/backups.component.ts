import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  BackupExecucao,
  BackupStatus,
  extrairMensagemErro,
} from '../../../core/models/repositorio.models';
import { BackupService } from '../../../core/services/backup.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-backups',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <div class="space-y-6">
      <!-- Resumo e ações imediatas -->
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="catalog-card p-4 rounded-lg">
          <span class="text-[11px] font-semibold text-stone-500 uppercase">Agendamento automático</span>
          <div class="text-base font-bold text-stone-900 mt-1">
            {{ status()?.habilitado ? 'Ativo (a cada ' + status()?.intervaloMinutos + ' min)' : 'Desativado' }}
          </div>
          @if (status()?.proximoAgendamento) {
            <p class="text-[11px] text-stone-500 mt-1">
              Próximo: {{ status()!.proximoAgendamento | date: 'dd/MM/yyyy HH:mm' }}
            </p>
          }
        </div>

        <div class="catalog-card p-4 rounded-lg">
          <span class="text-[11px] font-semibold text-stone-500 uppercase">Destino EC2</span>
          <div class="text-sm font-mono font-semibold text-stone-900 mt-1 truncate">
            {{ status()?.ec2Host ? status()!.ec2User + '@' + status()!.ec2Host : 'Não configurado' }}
          </div>
          <p class="text-[11px] text-stone-500 mt-1">
            Chave SSH: {{ status()?.chaveConfigurada ? 'Encontrada' : 'Ausente' }}
          </p>
        </div>

        <div class="catalog-card p-4 rounded-lg flex flex-col justify-between gap-2">
          <span class="text-[11px] font-semibold text-stone-500 uppercase">Ações manuais</span>
          <div class="flex flex-wrap gap-2">
            <button
              type="button"
              (click)="executarBackup()"
              [disabled]="executando() || status()?.emExecucao"
              class="btn-primary flex-1"
            >
              {{ executando() ? 'Sincronizando...' : 'Fazer backup agora' }}
            </button>
            <button
              type="button"
              (click)="restaurarBackup()"
              [disabled]="executando() || status()?.emExecucao"
              class="btn-secondary"
            >
              Restaurar da EC2
            </button>
          </div>
        </div>
      </div>

      <!-- Formulário de configuração -->
      <div class="catalog-card p-5 rounded-lg space-y-4">
        <div class="border-b border-stone-200 pb-2.5">
          <h3 class="text-base font-bold text-stone-900 serif-title">Configuração do backup na EC2</h3>
          <p class="text-xs text-stone-600">
            Atualize o IP da instância EC2 ou ajuste o intervalo periódico sem precisar reiniciar o servidor.
          </p>
        </div>

        <form [formGroup]="form" (ngSubmit)="salvarConfig()" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 text-xs">
          <div>
            <label for="ec2Host" class="block font-semibold text-stone-800 mb-1">IP / Host da EC2</label>
            <input id="ec2Host" type="text" formControlName="ec2Host" placeholder="3.95.214.123" class="input-academic text-xs" />
          </div>

          <div>
            <label for="ec2User" class="block font-semibold text-stone-800 mb-1">Usuário SSH</label>
            <input id="ec2User" type="text" formControlName="ec2User" placeholder="admin" class="input-academic text-xs" />
          </div>

          <div>
            <label for="ec2Dir" class="block font-semibold text-stone-800 mb-1">Diretório remoto</label>
            <input
              id="ec2Dir"
              type="text"
              formControlName="ec2Dir"
              placeholder="/home/admin/backup-repositorio"
              class="input-academic text-xs"
            />
          </div>

          <div>
            <label for="ec2Key" class="block font-semibold text-stone-800 mb-1">Caminho da chave .pem (opcional alterar)</label>
            <input
              id="ec2Key"
              type="text"
              formControlName="ec2Key"
              placeholder="~/Downloads/chavosa.pem"
              class="input-academic text-xs"
            />
          </div>

          <div>
            <label for="intervaloMinutos" class="block font-semibold text-stone-800 mb-1">Intervalo (minutos)</label>
            <input
              id="intervaloMinutos"
              type="number"
              min="1"
              max="10080"
              formControlName="intervaloMinutos"
              class="input-academic text-xs"
            />
          </div>

          <div class="flex items-end pb-1">
            <label class="flex items-center gap-2 cursor-pointer font-semibold text-stone-800">
              <input type="checkbox" formControlName="habilitado" class="rounded border-stone-300" />
              <span>Ativar backup automático periódico</span>
            </label>
          </div>

          <div class="sm:col-span-2 lg:col-span-3 flex justify-end pt-2 border-t border-stone-200">
            <button type="submit" [disabled]="form.invalid || salvando()" class="btn-primary px-5">
              {{ salvando() ? 'Salvando...' : 'Salvar configuração' }}
            </button>
          </div>
        </form>
      </div>

      <!-- Histórico -->
      <div class="catalog-card rounded-lg overflow-x-auto">
        <div class="px-4 py-3 bg-stone-100 border-b border-stone-200 flex items-center justify-between text-xs">
          <span class="font-bold text-stone-800">Histórico de execuções</span>
          <button type="button" (click)="carregarTudo()" class="text-amber-900 font-semibold hover:underline">
            Atualizar
          </button>
        </div>

        @if (historico().length === 0) {
          <div class="p-6 text-center text-xs text-stone-500">Nenhuma execução registrada ainda.</div>
        } @else {
          <table class="w-full text-left text-xs">
            <thead class="border-b border-stone-200 text-stone-600 uppercase text-[11px] bg-stone-50">
              <tr>
                <th class="p-3">Data</th>
                <th class="p-3">Operação</th>
                <th class="p-3">Origem</th>
                <th class="p-3">Resultado</th>
                <th class="p-3">Duração</th>
                <th class="p-3">Detalhes</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-stone-200">
              @for (item of historico(); track item.id) {
                <tr>
                  <td class="p-3 font-mono text-stone-600 whitespace-nowrap">
                    {{ item.iniciadoEm | date: 'dd/MM/yyyy HH:mm:ss' }}
                  </td>
                  <td class="p-3 font-semibold text-stone-800">{{ item.tipo }}</td>
                  <td class="p-3 text-stone-600">{{ item.origem }}</td>
                  <td class="p-3 whitespace-nowrap">
                    <span
                      [class]="
                        item.status === 'SUCESSO'
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                          : 'bg-rose-50 text-rose-800 border-rose-200'
                      "
                      class="px-2 py-0.5 rounded border text-[11px] font-semibold"
                    >
                      {{ item.status }}
                    </span>
                  </td>
                  <td class="p-3 font-mono text-stone-600 whitespace-nowrap">{{ item.duracaoMs }} ms</td>
                  <td class="p-3 text-stone-700">{{ item.mensagem }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </div>
  `,
})
export class BackupsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly backupService = inject(BackupService);
  private readonly toast = inject(ToastService);

  readonly status = signal<BackupStatus | null>(null);
  readonly historico = signal<BackupExecucao[]>([]);
  readonly salvando = signal(false);
  readonly executando = signal(false);

  readonly form = this.fb.nonNullable.group({
    habilitado: [false],
    intervaloMinutos: [60, [Validators.required, Validators.min(1), Validators.max(10080)]],
    ec2User: ['admin'],
    ec2Host: [''],
    ec2Key: [''],
    ec2Dir: ['/home/admin/backup-repositorio'],
  });

  ngOnInit(): void {
    this.carregarTudo();
  }

  carregarTudo(): void {
    this.backupService.obterStatus().subscribe({
      next: (s) => {
        this.status.set(s);
        this.form.patchValue({
          habilitado: s.habilitado,
          intervaloMinutos: s.intervaloMinutos,
          ec2User: s.ec2User,
          ec2Host: s.ec2Host,
          ec2Dir: s.ec2Dir,
        });
      },
      error: (err) => this.toast.erro(extrairMensagemErro(err, 'Erro ao consultar status de backup.')),
    });

    this.backupService.listarHistorico().subscribe({
      next: (lista) => this.historico.set(lista),
    });
  }

  salvarConfig(): void {
    if (this.form.invalid) return;
    this.salvando.set(true);

    const v = this.form.getRawValue();
    this.backupService
      .atualizarConfig({
        habilitado: v.habilitado,
        intervaloMinutos: v.intervaloMinutos,
        ec2User: v.ec2User,
        ec2Host: v.ec2Host,
        ec2Dir: v.ec2Dir,
        ...(v.ec2Key.trim() ? { ec2Key: v.ec2Key.trim() } : {}),
      })
      .subscribe({
        next: (novoStatus) => {
          this.status.set(novoStatus);
          this.toast.sucesso('Configuração de backup salva.');
          this.salvando.set(false);
        },
        error: (err) => {
          this.toast.erro(extrairMensagemErro(err, 'Erro ao atualizar configuração de backup.'));
          this.salvando.set(false);
        },
      });
  }

  executarBackup(): void {
    this.executando.set(true);
    this.backupService.executarAgora().subscribe({
      next: (res) => {
        if (res.status === 'SUCESSO') {
          this.toast.sucesso(res.mensagem);
        } else {
          this.toast.erro(res.mensagem);
        }
        this.executando.set(false);
        this.carregarTudo();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Falha ao executar backup.'));
        this.executando.set(false);
        this.carregarTudo();
      },
    });
  }

  restaurarBackup(): void {
    if (!confirm('Deseja baixar os arquivos da EC2 para o diretório local de storage?')) return;
    this.executando.set(true);
    this.backupService.restaurar().subscribe({
      next: (res) => {
        if (res.status === 'SUCESSO') {
          this.toast.sucesso(res.mensagem);
        } else {
          this.toast.erro(res.mensagem);
        }
        this.executando.set(false);
        this.carregarTudo();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Falha ao restaurar backup.'));
        this.executando.set(false);
        this.carregarTudo();
      },
    });
  }
}
