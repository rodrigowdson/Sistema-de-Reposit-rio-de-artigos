import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  Trabalho,
  extrairMensagemErro,
  rotuloTipo,
} from '../../core/models/repositorio.models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { TrabalhoService } from '../../core/services/trabalho.service';
import { PdfViewerComponent } from '../../shared/components/pdf-viewer.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

@Component({
  selector: 'app-trabalho-detalhe',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent, PdfViewerComponent],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <a routerLink="/" class="text-xs text-amber-900 font-semibold hover:underline">
          Voltar para o acervo
        </a>
        @if (trabalho()) {
          <app-status-badge [status]="trabalho()!.status" />
        }
      </div>

      @if (carregando()) {
        <div class="catalog-card p-8 rounded-lg text-center text-xs text-stone-500">
          Carregando ficha do trabalho...
        </div>
      } @else if (!trabalho()) {
        <div class="catalog-card p-8 rounded-lg text-center space-y-2">
          <p class="text-sm font-semibold text-stone-800">Trabalho não encontrado</p>
          <a routerLink="/" class="btn-secondary inline-block">Retornar ao catálogo</a>
        </div>
      } @else {
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div class="lg:col-span-2 space-y-5">
            <div class="catalog-card p-6 rounded-lg space-y-5">
              <div class="border-b border-stone-200 pb-4">
                <span class="bg-amber-50 text-amber-900 border border-amber-200 font-semibold px-2.5 py-0.5 rounded text-[11px] uppercase">
                  {{ nomeTipo() }}
                </span>
                <h2 class="text-xl md:text-2xl font-bold text-stone-900 serif-title mt-2 leading-snug">
                  {{ trabalho()!.titulo }}
                </h2>
              </div>

              <div class="bg-stone-50 p-4 rounded border border-stone-200 text-xs grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <span class="text-stone-500 block">Autor(es)</span>
                  <strong class="text-stone-900">{{ trabalho()!.autores.join(', ') }}</strong>
                </div>
                <div>
                  <span class="text-stone-500 block">Orientador</span>
                  <strong class="text-stone-900">{{ trabalho()!.orientadorNome || 'Não informado' }}</strong>
                </div>
                <div>
                  <span class="text-stone-500 block">Curso</span>
                  <strong class="text-stone-900">{{ trabalho()!.cursoNome || 'Não informado' }}</strong>
                </div>
                <div>
                  <span class="text-stone-500 block">Ano da defesa</span>
                  <strong class="text-stone-900">{{ trabalho()!.ano }}</strong>
                </div>
              </div>

              @if (trabalho()!.resumo) {
                <div>
                  <h3 class="text-xs font-bold text-stone-700 uppercase tracking-wider mb-1.5">Resumo</h3>
                  <p class="text-xs text-stone-700 leading-relaxed whitespace-pre-line bg-white p-4 rounded border border-stone-200">
                    {{ trabalho()!.resumo }}
                  </p>
                </div>
              }

              @if (palavrasChave().length > 0) {
                <div>
                  <h3 class="text-xs font-bold text-stone-700 uppercase tracking-wider mb-1.5">Palavras-chave</h3>
                  <div class="flex flex-wrap gap-1.5 text-xs">
                    @for (palavra of palavrasChave(); track palavra) {
                      <span class="bg-stone-100 text-stone-800 px-2.5 py-1 rounded border border-stone-300">
                        {{ palavra }}
                      </span>
                    }
                  </div>
                </div>
              }

              <div class="bg-amber-50/50 p-4 rounded border border-amber-200 space-y-2">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-xs font-bold text-amber-950">Referência sugerida (ABNT)</span>
                  <button
                    type="button"
                    (click)="copiarCitacao()"
                    class="text-xs text-amber-900 font-semibold hover:underline"
                  >
                    Copiar referência
                  </button>
                </div>
                <p class="text-xs text-stone-800 font-mono bg-white p-3 rounded border border-amber-200/80 leading-relaxed">
                  {{ citacaoAbnt() }}
                </p>
              </div>
            </div>
          </div>

          <div class="space-y-4">
            <div class="catalog-card p-5 rounded-lg space-y-3">
              <h3 class="text-sm font-bold text-stone-900 border-b border-stone-200 pb-2">
                Acesso ao arquivo
              </h3>

              <button
                type="button"
                (click)="baixarPdf()"
                [disabled]="baixando()"
                class="w-full btn-primary py-2.5 text-sm"
              >
                {{ baixando() ? 'Preparando download...' : 'Baixar PDF' }}
              </button>

              <button
                type="button"
                (click)="mostrarVisualizador.set(!mostrarVisualizador())"
                class="w-full btn-secondary py-2"
              >
                {{ mostrarVisualizador() ? 'Ocultar visualizador' : 'Visualizar PDF na página' }}
              </button>

              <div class="pt-3 border-t border-stone-200 space-y-2 text-xs text-stone-600">
                <div class="flex justify-between gap-2">
                  <span>Arquivo:</span>
                  <span class="text-stone-900 font-medium truncate max-w-[180px]">
                    {{ trabalho()!.arquivoNomeOriginal }}
                  </span>
                </div>
                <div class="flex justify-between gap-2">
                  <span>Downloads registrados:</span>
                  <strong class="text-stone-900 font-mono">{{ trabalho()!.totalDownloads }}</strong>
                </div>
                @if (trabalho()!.arquivoHashSha256) {
                  <div class="flex justify-between gap-2">
                    <span>SHA-256:</span>
                    <span class="font-mono text-[10px] text-stone-500">
                      {{ trabalho()!.arquivoHashSha256!.slice(0, 12) }}...
                    </span>
                  </div>
                }
              </div>

              @if (podeAlterar()) {
                <div class="pt-3 border-t border-stone-200 flex gap-2">
                  <a [routerLink]="['/trabalhos', trabalho()!.id, 'editar']" class="btn-secondary flex-1 text-center">
                    Editar ficha
                  </a>
                  <button type="button" (click)="excluir()" class="btn-danger flex-1">
                    Excluir
                  </button>
                </div>
              }
            </div>
          </div>
        </div>

        @if (mostrarVisualizador()) {
          <app-pdf-viewer [trabalhoId]="trabalho()!.id" />
        }
      }
    </div>
  `,
})
export class TrabalhoDetalheComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly trabalhoService = inject(TrabalhoService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();
  readonly trabalho = signal<Trabalho | null>(null);
  readonly carregando = signal(true);
  readonly baixando = signal(false);
  readonly mostrarVisualizador = signal(true);

  podeAlterar(): boolean {
    const t = this.trabalho();
    if (!t || !this.auth.estaAutenticado()) return false;
    if (this.auth.podeModerar()) return true;
    return t.status === 'PENDENTE' && t.enviadoPorId === this.auth.usuario()?.id;
  }

  excluir(): void {
    const t = this.trabalho();
    if (!t) return;
    if (!confirm(`Deseja realmente excluir "${t.titulo}"? O arquivo PDF também será removido.`)) {
      return;
    }
    this.trabalhoService.excluir(t.id).subscribe({
      next: () => {
        this.toast.sucesso('Trabalho excluído com sucesso.');
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível excluir o trabalho.'));
      },
    });
  }

  ngOnInit(): void {
    const trabalhoId = Number(this.id());
    if (!trabalhoId) {
      this.carregando.set(false);
      return;
    }

    this.trabalhoService.buscarPorId(trabalhoId).subscribe({
      next: (t) => {
        this.trabalho.set(t);
        this.carregando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Trabalho não encontrado.'));
        this.carregando.set(false);
      },
    });
  }

  nomeTipo(): string {
    return rotuloTipo(this.trabalho()?.tipo);
  }

  palavrasChave(): string[] {
    const raw = this.trabalho()?.palavrasChave;
    if (!raw) return [];
    return raw
      .split(/[;,]/)
      .map((p) => p.trim())
      .filter((p) => p.length > 0);
  }

  citacaoAbnt(): string {
    const t = this.trabalho();
    if (!t) return '';
    const autores = t.autores.map((a) => a.toUpperCase()).join('; ');
    const orientador = t.orientadorNome ? ` Orientador: ${t.orientadorNome}.` : '';
    const curso = t.cursoNome ? ` (${t.cursoNome})` : '';
    return `${autores}. ${t.titulo}.${orientador} ${t.ano}. ${rotuloTipo(t.tipo)}${curso}.`;
  }

  copiarCitacao(): void {
    navigator.clipboard.writeText(this.citacaoAbnt()).then(
      () => this.toast.sucesso('Referência copiada para a área de transferência.'),
      () => this.toast.erro('Não foi possível copiar automaticamente.')
    );
  }

  baixarPdf(): void {
    const t = this.trabalho();
    if (!t) return;

    this.baixando.set(true);
    this.trabalhoService.obterPdfBlob(t.id, false).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = t.arquivoNomeOriginal || `trabalho-${t.id}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
        this.trabalho.update((atual) =>
          atual ? { ...atual, totalDownloads: atual.totalDownloads + 1 } : atual
        );
        this.baixando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível baixar o PDF.'));
        this.baixando.set(false);
      },
    });
  }
}
