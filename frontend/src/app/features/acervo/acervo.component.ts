import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Curso,
  Orientador,
  Page,
  TIPOS_TRABALHO,
  TipoTrabalho,
  Trabalho,
  TrabalhoFiltro,
  extrairMensagemErro,
  rotuloTipo,
} from '../../core/models/repositorio.models';
import { CursoService } from '../../core/services/curso.service';
import { OrientadorService } from '../../core/services/orientador.service';
import { ToastService } from '../../core/services/toast.service';
import { TrabalhoService } from '../../core/services/trabalho.service';
import { PaginatorComponent } from '../../shared/components/paginator.component';

@Component({
  selector: 'app-acervo',
  standalone: true,
  imports: [FormsModule, RouterLink, PaginatorComponent],
  template: `
    <div class="space-y-6">
      <!-- Barra de busca -->
      <div class="catalog-card p-4 rounded-lg">
        <form (ngSubmit)="pesquisar(0)" class="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            name="termo"
            [(ngModel)]="termo"
            placeholder="Busque por título, resumo, palavras-chave ou autor..."
            class="input-academic flex-1"
          />
          <button type="submit" class="btn-primary px-6 py-2.5 text-sm">
            Buscar no acervo
          </button>
        </form>
        <p class="mt-2 text-xs text-stone-500">
          A pesquisa combina termos em título, resumo, palavras-chave e nomes de autores.
        </p>
      </div>

      <!-- Grade 2 colunas: Filtros + Lista -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
        <aside class="space-y-4">
          <div class="catalog-card p-4 rounded-lg space-y-4 text-xs">
            <div class="flex items-center justify-between pb-2 border-b border-stone-200">
              <h2 class="font-bold text-stone-800 uppercase tracking-wide">Filtros</h2>
              <button
                type="button"
                (click)="limparFiltros()"
                class="text-stone-500 hover:text-amber-900 underline text-[11px]"
              >
                Limpar
              </button>
            </div>

            <div>
              <label for="filtroTipo" class="block font-semibold text-stone-700 mb-1.5">Tipo de documento</label>
              <select
                id="filtroTipo"
                [(ngModel)]="tipoSelecionado"
                (ngModelChange)="pesquisar(0)"
                class="input-academic text-xs py-1.5"
              >
                <option value="">Todos os tipos</option>
                @for (t of tipos; track t.valor) {
                  <option [value]="t.valor">{{ t.rotulo }}</option>
                }
              </select>
            </div>

            <div class="pt-3 border-t border-stone-200">
              <label for="filtroCurso" class="block font-semibold text-stone-700 mb-1.5">Curso</label>
              <select
                id="filtroCurso"
                [(ngModel)]="cursoIdSelecionado"
                (ngModelChange)="pesquisar(0)"
                class="input-academic text-xs py-1.5"
              >
                <option [ngValue]="null">Todos os cursos</option>
                @for (c of cursos(); track c.id) {
                  <option [ngValue]="c.id">{{ c.nome }}{{ c.sigla ? ' (' + c.sigla + ')' : '' }}</option>
                }
              </select>
            </div>

            <div class="pt-3 border-t border-stone-200">
              <label for="filtroOrientador" class="block font-semibold text-stone-700 mb-1.5">Orientador</label>
              <select
                id="filtroOrientador"
                [(ngModel)]="orientadorIdSelecionado"
                (ngModelChange)="pesquisar(0)"
                class="input-academic text-xs py-1.5"
              >
                <option [ngValue]="null">Todos os orientadores</option>
                @for (o of orientadores(); track o.id) {
                  <option [ngValue]="o.id">{{ o.nome }}</option>
                }
              </select>
            </div>

            <div class="pt-3 border-t border-stone-200">
              <span class="block font-semibold text-stone-700 mb-1.5">Ano da defesa</span>
              @if (anos().length === 0) {
                <p class="text-[11px] text-stone-400">Nenhum ano disponível</p>
              } @else {
                <div class="grid grid-cols-2 gap-1.5 text-[11px]">
                  <button
                    type="button"
                    (click)="selecionarAno(null)"
                    [class]="
                      anoSelecionado === null
                        ? 'bg-amber-100 text-amber-950 border border-amber-400 rounded py-1 font-semibold'
                        : 'bg-stone-50 border border-stone-200 rounded py-1 text-stone-700 hover:bg-stone-100'
                    "
                  >
                    Todos
                  </button>
                  @for (ano of anos(); track ano) {
                    <button
                      type="button"
                      (click)="selecionarAno(ano)"
                      [class]="
                        anoSelecionado === ano
                          ? 'bg-amber-100 text-amber-950 border border-amber-400 rounded py-1 font-semibold'
                          : 'bg-stone-50 border border-stone-200 rounded py-1 text-stone-700 hover:bg-stone-100'
                      "
                    >
                      {{ ano }}
                    </button>
                  }
                </div>
              }
            </div>
          </div>
        </aside>

        <section class="md:col-span-3 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2 text-xs text-stone-600 pb-1">
            <span>
              Resultados: <strong>{{ pagina()?.totalElements ?? 0 }}</strong> trabalho(s) encontrado(s)
            </span>
            <div class="flex items-center gap-2">
              <label for="ordenacao" class="text-stone-500">Ordenar por:</label>
              <select
                id="ordenacao"
                [(ngModel)]="ordenacao"
                (ngModelChange)="pesquisar(0)"
                class="bg-white border border-stone-300 rounded px-2 py-1 text-xs text-stone-800"
              >
                <option value="ano,desc">Mais recentes</option>
                <option value="totalDownloads,desc">Mais baixados</option>
                <option value="titulo,asc">Título (A-Z)</option>
              </select>
            </div>
          </div>

          @if (carregando()) {
            <div class="catalog-card p-8 rounded-lg text-center text-xs text-stone-500">
              Carregando catálogo...
            </div>
          } @else if (!pagina() || pagina()!.content.length === 0) {
            <div class="catalog-card p-8 rounded-lg text-center space-y-2">
              <p class="text-sm font-semibold text-stone-700">Nenhum trabalho encontrado</p>
              <p class="text-xs text-stone-500">
                Tente ajustar os filtros ou pesquisar por outros termos.
              </p>
            </div>
          } @else {
            @for (trabalho of pagina()!.content; track trabalho.id) {
              <article class="catalog-card-interactive p-4 rounded-lg space-y-2.5">
                <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="bg-amber-50 text-amber-900 border border-amber-200 font-semibold px-2 py-0.5 rounded text-[11px]">
                      {{ nomeTipo(trabalho.tipo) }}
                    </span>
                    <span class="text-stone-600">Ano: <strong>{{ trabalho.ano }}</strong></span>
                    @if (trabalho.cursoNome) {
                      <span class="text-stone-300">|</span>
                      <span class="text-stone-700 font-medium">{{ trabalho.cursoNome }}</span>
                    }
                  </div>
                  <span class="text-stone-500 font-mono text-[11px]">
                    {{ trabalho.totalDownloads }} download(s)
                  </span>
                </div>

                <h3 class="text-base font-bold text-stone-900 serif-title leading-snug">
                  <a [routerLink]="['/trabalhos', trabalho.id]" class="hover:text-amber-900 transition">
                    {{ trabalho.titulo }}
                  </a>
                </h3>

                @if (trabalho.resumo) {
                  <p class="text-xs text-stone-600 leading-relaxed line-clamp-2">
                    {{ trabalho.resumo }}
                  </p>
                }

                <div class="pt-2.5 border-t border-stone-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                  <div class="text-stone-600 space-y-0.5 sm:space-y-0">
                    <span><strong>Autores:</strong> {{ trabalho.autores.join(', ') }}</span>
                    @if (trabalho.orientadorNome) {
                      <span class="mx-1.5 text-stone-400">|</span>
                      <span><strong>Orientador:</strong> {{ trabalho.orientadorNome }}</span>
                    }
                  </div>

                  <div class="flex items-center gap-2 shrink-0">
                    <a [routerLink]="['/trabalhos', trabalho.id]" class="btn-secondary">
                      Ver ficha
                    </a>
                    <button
                      type="button"
                      (click)="baixarPdf(trabalho)"
                      [disabled]="baixandoId() === trabalho.id"
                      class="btn-primary"
                    >
                      {{ baixandoId() === trabalho.id ? 'Baixando...' : 'Baixar PDF' }}
                    </button>
                  </div>
                </div>
              </article>
            }

            <app-paginator
              [page]="pagina()!.number"
              [totalPages]="pagina()!.totalPages"
              (mudarPagina)="pesquisar($event)"
            />
          }
        </section>
      </div>
    </div>
  `,
})
export class AcervoComponent implements OnInit {
  private readonly trabalhoService = inject(TrabalhoService);
  private readonly cursoService = inject(CursoService);
  private readonly orientadorService = inject(OrientadorService);
  private readonly toast = inject(ToastService);

  readonly tipos = TIPOS_TRABALHO;
  readonly cursos = signal<Curso[]>([]);
  readonly orientadores = signal<Orientador[]>([]);
  readonly anos = signal<number[]>([]);
  readonly pagina = signal<Page<Trabalho> | null>(null);
  readonly carregando = signal(false);
  readonly baixandoId = signal<number | null>(null);

  termo = '';
  tipoSelecionado: TipoTrabalho | '' = '';
  cursoIdSelecionado: number | null = null;
  orientadorIdSelecionado: number | null = null;
  anoSelecionado: number | null = null;
  ordenacao = 'ano,desc';

  ngOnInit(): void {
    this.cursoService.listar().subscribe({ next: (c) => this.cursos.set(c) });
    this.orientadorService.listar(false).subscribe({ next: (o) => this.orientadores.set(o) });
    this.trabalhoService.listarAnos().subscribe({ next: (a) => this.anos.set(a) });
    this.pesquisar(0);
  }

  pesquisar(page: number): void {
    const filtro: TrabalhoFiltro = {
      termo: this.termo || null,
      tipo: this.tipoSelecionado || null,
      cursoId: this.cursoIdSelecionado,
      orientadorId: this.orientadorIdSelecionado,
      ano: this.anoSelecionado,
    };

    this.carregando.set(true);
    this.trabalhoService.buscar(filtro, page, 10, this.ordenacao).subscribe({
      next: (res) => {
        this.pagina.set(res);
        this.carregando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao consultar o acervo.'));
        this.carregando.set(false);
      },
    });
  }

  selecionarAno(ano: number | null): void {
    this.anoSelecionado = ano;
    this.pesquisar(0);
  }

  limparFiltros(): void {
    this.termo = '';
    this.tipoSelecionado = '';
    this.cursoIdSelecionado = null;
    this.orientadorIdSelecionado = null;
    this.anoSelecionado = null;
    this.ordenacao = 'ano,desc';
    this.pesquisar(0);
  }

  baixarPdf(trabalho: Trabalho): void {
    this.baixandoId.set(trabalho.id);
    this.trabalhoService.obterPdfBlob(trabalho.id, false).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = trabalho.arquivoNomeOriginal || `trabalho-${trabalho.id}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
        this.baixandoId.set(null);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível baixar o PDF.'));
        this.baixandoId.set(null);
      },
    });
  }

  nomeTipo(tipo: TipoTrabalho): string {
    return rotuloTipo(tipo);
  }
}
