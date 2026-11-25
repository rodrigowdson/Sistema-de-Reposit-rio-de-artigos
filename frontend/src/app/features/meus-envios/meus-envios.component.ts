import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  Page,
  Trabalho,
  extrairMensagemErro,
  rotuloTipo,
} from '../../core/models/repositorio.models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { TrabalhoService } from '../../core/services/trabalho.service';
import { PaginatorComponent } from '../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

@Component({
  selector: 'app-meus-envios',
  standalone: true,
  imports: [RouterLink, DatePipe, StatusBadgeComponent, PaginatorComponent],
  template: `
    <div class="space-y-5">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 class="text-xl font-bold text-stone-900 serif-title">Meus envios</h2>
          <p class="text-xs text-stone-600">
            Acompanhe a situação dos trabalhos enviados para análise e publicação no acervo.
          </p>
        </div>
        <a routerLink="/enviar" class="btn-primary self-start sm:self-auto">Novo envio</a>
      </div>

      @if (carregando()) {
        <div class="catalog-card p-8 rounded-lg text-center text-xs text-stone-500">
          Carregando seus envios...
        </div>
      } @else if (!pagina() || pagina()!.content.length === 0) {
        <div class="catalog-card p-8 rounded-lg text-center space-y-3">
          <p class="text-sm font-semibold text-stone-700">Você ainda não enviou nenhum trabalho.</p>
          <a routerLink="/enviar" class="btn-primary inline-block">Submeter primeiro trabalho</a>
        </div>
      } @else {
        <div class="catalog-card rounded-lg overflow-x-auto">
          <table class="w-full text-left text-xs">
            <thead class="bg-stone-100 border-b border-stone-200 text-stone-700 font-semibold uppercase text-[11px]">
              <tr>
                <th class="p-3">Título e curso</th>
                <th class="p-3">Tipo</th>
                <th class="p-3">Situação</th>
                <th class="p-3">Downloads</th>
                <th class="p-3">Enviado em</th>
                <th class="p-3 text-right">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-stone-200">
              @for (item of pagina()!.content; track item.id) {
                <tr class="hover:bg-stone-50/70">
                  <td class="p-3">
                    <div class="font-semibold text-stone-900">{{ item.titulo }}</div>
                    <div class="text-[11px] text-stone-500 mt-0.5">
                      {{ item.cursoNome }} · Orientador: {{ item.orientadorNome }}
                    </div>
                  </td>
                  <td class="p-3 whitespace-nowrap">{{ nomeTipo(item.tipo) }}</td>
                  <td class="p-3 whitespace-nowrap">
                    <app-status-badge [status]="item.status" />
                  </td>
                  <td class="p-3 font-mono text-stone-700">{{ item.totalDownloads }}</td>
                  <td class="p-3 text-stone-600 whitespace-nowrap">
                    {{ item.criadoEm | date: 'dd/MM/yyyy' }}
                  </td>
                  <td class="p-3 text-right whitespace-nowrap space-x-3">
                    <a [routerLink]="['/trabalhos', item.id]" class="text-amber-900 font-semibold hover:underline">
                      Ver
                    </a>
                    @if (podeAlterar(item)) {
                      <a [routerLink]="['/trabalhos', item.id, 'editar']" class="text-stone-700 hover:underline">
                        Editar
                      </a>
                      <button
                        type="button"
                        (click)="excluir(item)"
                        class="text-rose-700 hover:underline"
                      >
                        Excluir
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <app-paginator
          [page]="pagina()!.number"
          [totalPages]="pagina()!.totalPages"
          (mudarPagina)="carregar($event)"
        />
      }
    </div>
  `,
})
export class MeusEnviosComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly trabalhoService = inject(TrabalhoService);
  private readonly toast = inject(ToastService);

  readonly pagina = signal<Page<Trabalho> | null>(null);
  readonly carregando = signal(true);

  ngOnInit(): void {
    this.carregar(0);
  }

  podeAlterar(item: Trabalho): boolean {
    return this.auth.podeModerar() || item.status === 'PENDENTE';
  }

  carregar(page: number): void {
    this.carregando.set(true);
    this.trabalhoService.listarMeus(page, 10).subscribe({
      next: (res) => {
        this.pagina.set(res);
        this.carregando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao carregar seus envios.'));
        this.carregando.set(false);
      },
    });
  }

  excluir(item: Trabalho): void {
    if (!confirm(`Deseja realmente excluir "${item.titulo}"? O arquivo PDF também será removido.`)) {
      return;
    }
    this.trabalhoService.excluir(item.id).subscribe({
      next: () => {
        this.toast.sucesso('Trabalho excluído com sucesso.');
        const paginaAtual = this.pagina()?.number ?? 0;
        const itensRestantes = (this.pagina()?.content.length ?? 1) - 1;
        const proximaPagina = itensRestantes === 0 && paginaAtual > 0 ? paginaAtual - 1 : paginaAtual;
        this.carregar(proximaPagina);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível excluir o trabalho.'));
      },
    });
  }

  nomeTipo(tipo: Trabalho['tipo']): string {
    return rotuloTipo(tipo);
  }
}
