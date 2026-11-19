import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Page,
  StatusTrabalho,
  Trabalho,
  extrairMensagemErro,
  rotuloTipo,
} from '../../../core/models/repositorio.models';
import { ToastService } from '../../../core/services/toast.service';
import { TrabalhoService } from '../../../core/services/trabalho.service';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { PdfViewerComponent } from '../../../shared/components/pdf-viewer.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';

@Component({
  selector: 'app-moderacao',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, StatusBadgeComponent, PaginatorComponent, PdfViewerComponent],
  template: `
    <div class="space-y-4">
      <div class="catalog-card p-4 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h3 class="text-base font-bold text-stone-900 serif-title">Fila de homologação</h3>
          <p class="text-xs text-stone-600">
            Revise os metadados e o PDF antes de liberar o trabalho no catálogo público.
          </p>
        </div>

        <div class="flex items-center gap-2 text-xs">
          <label for="statusFiltro" class="text-stone-600 font-semibold">Situação:</label>
          <select
            id="statusFiltro"
            [(ngModel)]="statusFiltro"
            (ngModelChange)="carregar(0)"
            class="bg-stone-50 border border-stone-300 rounded px-2.5 py-1.5 text-xs text-stone-900"
          >
            <option value="PENDENTE">Pendentes</option>
            <option value="PUBLICADO">Publicados</option>
            <option value="ARQUIVADO">Arquivados</option>
            <option value="">Todos</option>
          </select>
        </div>
      </div>

      @if (carregando()) {
        <div class="catalog-card p-8 rounded-lg text-center text-xs text-stone-500">
          Carregando trabalhos...
        </div>
      } @else if (!pagina() || pagina()!.content.length === 0) {
        <div class="catalog-card p-8 rounded-lg text-center text-xs text-stone-500">
          Nenhum trabalho encontrado para o filtro selecionado.
        </div>
      } @else {
        @for (item of pagina()!.content; track item.id) {
          <div class="catalog-card p-5 rounded-lg space-y-3 border-l-4" [class]="bordaStatus(item.status)">
            <div class="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
              <div class="space-y-1.5">
                <div class="flex flex-wrap items-center gap-2 text-xs">
                  <app-status-badge [status]="item.status" />
                  <span class="text-stone-500">{{ nomeTipo(item.tipo) }}</span>
                  <span class="text-stone-300">|</span>
                  <span class="text-stone-500">Ano: {{ item.ano }}</span>
                  <span class="text-stone-300">|</span>
                  <span class="text-stone-500">Enviado em {{ item.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</span>
                </div>

                <h4 class="text-base font-bold text-stone-900 serif-title">{{ item.titulo }}</h4>

                <p class="text-xs text-stone-600">
                  <strong>Autores:</strong> {{ item.autores.join(', ') }}
                  <span class="mx-1.5 text-stone-400">|</span>
                  <strong>Curso:</strong> {{ item.cursoNome }}
                  <span class="mx-1.5 text-stone-400">|</span>
                  <strong>Orientador:</strong> {{ item.orientadorNome }}
                </p>
              </div>

              <div class="flex flex-wrap items-center gap-2 shrink-0">
                @if (item.status !== 'PUBLICADO') {
                  <button
                    type="button"
                    (click)="alterarStatus(item, 'PUBLICADO')"
                    class="bg-emerald-700 hover:bg-emerald-800 text-white px-3.5 py-2 rounded text-xs font-semibold transition"
                  >
                    Aprovar e publicar
                  </button>
                }
                @if (item.status !== 'ARQUIVADO') {
                  <button
                    type="button"
                    (click)="alterarStatus(item, 'ARQUIVADO')"
                    class="btn-danger"
                  >
                    Arquivar
                  </button>
                }
                @if (item.status !== 'PENDENTE') {
                  <button
                    type="button"
                    (click)="alterarStatus(item, 'PENDENTE')"
                    class="btn-secondary"
                  >
                    Voltar para pendente
                  </button>
                }
              </div>
            </div>

            <div class="bg-stone-50 px-3 py-2 rounded border border-stone-200 text-xs flex flex-wrap items-center justify-between gap-2 text-stone-600">
              <span>
                Arquivo: <code class="font-mono text-stone-800">{{ item.arquivoNomeOriginal }}</code>
              </span>
              <div class="flex items-center gap-4">
                <button
                  type="button"
                  (click)="alternarPreview(item.id)"
                  class="text-amber-900 font-semibold hover:underline"
                >
                  {{ previewAbertoId() === item.id ? 'Fechar pré-visualização' : 'Pré-visualizar PDF' }}
                </button>
                <a [routerLink]="['/trabalhos', item.id]" class="text-stone-700 font-semibold hover:underline">
                  Abrir ficha
                </a>
              </div>
            </div>

            @if (previewAbertoId() === item.id) {
              <app-pdf-viewer [trabalhoId]="item.id" />
            }
          </div>
        }

        <app-paginator
          [page]="pagina()!.number"
          [totalPages]="pagina()!.totalPages"
          (mudarPagina)="carregar($event)"
        />
      }
    </div>
  `,
})
export class ModeracaoComponent implements OnInit {
  private readonly trabalhoService = inject(TrabalhoService);
  private readonly toast = inject(ToastService);

  readonly pagina = signal<Page<Trabalho> | null>(null);
  readonly carregando = signal(true);
  readonly previewAbertoId = signal<number | null>(null);

  statusFiltro: StatusTrabalho | '' = 'PENDENTE';

  ngOnInit(): void {
    this.carregar(0);
  }

  carregar(page: number): void {
    this.carregando.set(true);
    this.trabalhoService
      .buscar({ status: this.statusFiltro || null }, page, 10, 'criadoEm,desc')
      .subscribe({
        next: (res) => {
          this.pagina.set(res);
          this.carregando.set(false);
        },
        error: (err) => {
          this.toast.erro(extrairMensagemErro(err, 'Erro ao carregar fila de moderação.'));
          this.carregando.set(false);
        },
      });
  }

  alterarStatus(item: Trabalho, novoStatus: StatusTrabalho): void {
    this.trabalhoService.alterarStatus(item.id, novoStatus).subscribe({
      next: () => {
        this.toast.sucesso(`Status alterado para ${novoStatus}.`);
        if (this.previewAbertoId() === item.id) {
          this.previewAbertoId.set(null);
        }
        this.carregar(this.pagina()?.number ?? 0);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível alterar o status.'));
      },
    });
  }

  alternarPreview(id: number): void {
    this.previewAbertoId.set(this.previewAbertoId() === id ? null : id);
  }

  bordaStatus(status: StatusTrabalho): string {
    switch (status) {
      case 'PENDENTE':
        return 'border-l-amber-700';
      case 'PUBLICADO':
        return 'border-l-emerald-700';
      case 'ARQUIVADO':
        return 'border-l-stone-400';
    }
  }

  nomeTipo(tipo: Trabalho['tipo']): string {
    return rotuloTipo(tipo);
  }
}
