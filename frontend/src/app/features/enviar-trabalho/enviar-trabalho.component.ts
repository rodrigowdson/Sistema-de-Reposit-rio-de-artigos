import { HttpEventType } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  Curso,
  Orientador,
  TIPOS_TRABALHO,
  TipoTrabalho,
  TrabalhoRequest,
  extrairMensagemErro,
} from '../../core/models/repositorio.models';
import { AuthService } from '../../core/services/auth.service';
import { CursoService } from '../../core/services/curso.service';
import { OrientadorService } from '../../core/services/orientador.service';
import { ToastService } from '../../core/services/toast.service';
import { TrabalhoService } from '../../core/services/trabalho.service';

@Component({
  selector: 'app-enviar-trabalho',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="max-w-3xl mx-auto">
      <div class="catalog-card p-6 md:p-8 rounded-lg space-y-6">
        <div class="border-b border-stone-200 pb-4">
          <span class="text-[11px] font-bold text-amber-900 uppercase tracking-wider">
            {{ modoEdicao() ? 'Atualização de metadados' : 'Depósito de trabalho final' }}
          </span>
          <h2 class="text-xl md:text-2xl font-bold text-stone-900 serif-title mt-1">
            {{ modoEdicao() ? 'Editar trabalho' : 'Submissão de trabalho acadêmico' }}
          </h2>
          <p class="text-xs text-stone-600 mt-1">
            {{
              modoEdicao()
                ? 'Atualize os dados catalográficos do trabalho enviado.'
                : 'Envie a versão final em PDF aprovada pela banca para inclusão no repositório.'
            }}
          </p>
        </div>

        <form [formGroup]="form" (ngSubmit)="salvar()" class="space-y-4 text-xs">
          <div>
            <label for="titulo" class="block font-semibold text-stone-800 mb-1">Título oficial *</label>
            <input
              id="titulo"
              type="text"
              formControlName="titulo"
              placeholder="Digite o título conforme consta na folha de aprovação"
              class="input-academic"
            />
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label for="tipo" class="block font-semibold text-stone-800 mb-1">Tipo de trabalho *</label>
              <select id="tipo" formControlName="tipo" class="input-academic text-xs">
                @for (t of tipos; track t.valor) {
                  <option [value]="t.valor">{{ t.rotulo }}</option>
                }
              </select>
            </div>

            <div>
              <label for="ano" class="block font-semibold text-stone-800 mb-1">Ano da defesa *</label>
              <input id="ano" type="number" min="1900" max="2100" formControlName="ano" class="input-academic text-xs" />
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label for="cursoId" class="block font-semibold text-stone-800 mb-1">Curso *</label>
              <select id="cursoId" formControlName="cursoId" class="input-academic text-xs">
                <option [ngValue]="null">Selecione um curso</option>
                @for (c of cursos(); track c.id) {
                  <option [ngValue]="c.id">{{ c.nome }}{{ c.sigla ? ' (' + c.sigla + ')' : '' }}</option>
                }
              </select>
            </div>

            <div>
              <label for="orientadorId" class="block font-semibold text-stone-800 mb-1">Orientador *</label>
              <select id="orientadorId" formControlName="orientadorId" class="input-academic text-xs">
                <option [ngValue]="null">Selecione um orientador</option>
                @for (o of orientadores(); track o.id) {
                  <option [ngValue]="o.id">{{ o.titulacao ? o.titulacao + ' ' : '' }}{{ o.nome }}</option>
                }
              </select>
            </div>
          </div>

          <div>
            <label for="autoresTexto" class="block font-semibold text-stone-800 mb-1">
              Nome completo dos autores * (separados por vírgula)
            </label>
            <input
              id="autoresTexto"
              type="text"
              formControlName="autoresTexto"
              placeholder="Ex: Lucas Mendes da Silva, Beatriz Costa Lima"
              class="input-academic text-xs"
            />
          </div>

          <div>
            <label for="resumo" class="block font-semibold text-stone-800 mb-1">Resumo</label>
            <textarea
              id="resumo"
              rows="4"
              formControlName="resumo"
              placeholder="Insira o resumo do trabalho"
              class="input-academic text-xs"
            ></textarea>
          </div>

          <div>
            <label for="palavrasChave" class="block font-semibold text-stone-800 mb-1">
              Palavras-chave (separadas por ponto e vírgula)
            </label>
            <input
              id="palavrasChave"
              type="text"
              formControlName="palavrasChave"
              placeholder="Ex: Engenharia de Software; Sistemas Distribuídos; Arquitetura"
              class="input-academic text-xs"
            />
          </div>

          @if (!modoEdicao()) {
            <div class="pt-2">
              <label class="block font-semibold text-stone-800 mb-1">Arquivo em formato PDF *</label>
              <div
                (dragover)="aoArrastarSobre($event)"
                (dragleave)="arrastando.set(false)"
                (drop)="aoSoltarArquivo($event)"
                [class]="
                  arrastando()
                    ? 'border-amber-800 bg-amber-50/50'
                    : 'border-stone-300 bg-stone-50 hover:bg-stone-100'
                "
                class="border-2 border-dashed p-6 rounded text-center transition cursor-pointer"
                (click)="inputArquivo.click()"
              >
                <input
                  #inputArquivo
                  type="file"
                  accept="application/pdf,.pdf"
                  class="hidden"
                  (change)="aoSelecionarArquivo($event)"
                />
                @if (arquivoSelecionado()) {
                  <p class="text-sm font-semibold text-stone-900">{{ arquivoSelecionado()!.name }}</p>
                  <p class="text-xs text-stone-500 mt-1">
                    {{ formatarTamanho(arquivoSelecionado()!.size) }} — clique para trocar o arquivo
                  </p>
                } @else {
                  <p class="text-sm font-semibold text-stone-800">
                    Arraste o PDF para esta área ou clique para selecionar
                  </p>
                  <p class="text-xs text-stone-500 mt-1">
                    Apenas arquivos PDF válidos (máximo 100 MB)
                  </p>
                }
              </div>

              @if (enviando() && progresso() > 0) {
                <div class="mt-3 space-y-1">
                  <div class="flex justify-between text-[11px] text-stone-600">
                    <span>Enviando arquivo...</span>
                    <span class="font-mono">{{ progresso() }}%</span>
                  </div>
                  <div class="w-full bg-stone-200 rounded h-2 overflow-hidden">
                    <div
                      class="bg-[#1c2430] h-2 transition-all duration-150"
                      [style.width.%]="progresso()"
                    ></div>
                  </div>
                </div>
              }
            </div>
          }

          <div class="pt-4 border-t border-stone-200 flex justify-end gap-3">
            <a routerLink="/meus-envios" class="btn-secondary">Cancelar</a>
            <button
              type="submit"
              [disabled]="form.invalid || enviando() || (!modoEdicao() && !arquivoSelecionado())"
              class="btn-primary px-6"
            >
              {{ enviando() ? 'Salvando...' : modoEdicao() ? 'Salvar alterações' : 'Protocolar submissão' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class EnviarTrabalhoComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly trabalhoService = inject(TrabalhoService);
  private readonly cursoService = inject(CursoService);
  private readonly orientadorService = inject(OrientadorService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly id = input<string>();
  readonly tipos = TIPOS_TRABALHO;
  readonly cursos = signal<Curso[]>([]);
  readonly orientadores = signal<Orientador[]>([]);
  readonly arquivoSelecionado = signal<File | null>(null);
  readonly arrastando = signal(false);
  readonly enviando = signal(false);
  readonly progresso = signal(0);
  readonly modoEdicao = signal(false);

  private orientadorVinculado: { id: number; nome: string } | null = null;

  readonly form = this.fb.group({
    titulo: ['', [Validators.required, Validators.maxLength(300)]],
    tipo: ['TCC' as TipoTrabalho, [Validators.required]],
    ano: [new Date().getFullYear(), [Validators.required, Validators.min(1900), Validators.max(2100)]],
    cursoId: [null as number | null, [Validators.required]],
    orientadorId: [null as number | null, [Validators.required]],
    autoresTexto: ['', [Validators.required]],
    resumo: [''],
    palavrasChave: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    const trabalhoId = this.id() ? Number(this.id()) : null;
    this.modoEdicao.set(!!trabalhoId);

    this.cursoService.listar().subscribe({
      next: (lista) => this.cursos.set(lista),
    });

    this.orientadorService.listar(!this.auth.podeModerar()).subscribe({
      next: (lista) => {
        this.orientadores.set(lista);
        this.garantirOrientadorVinculadoNaLista();
      },
    });

    if (trabalhoId) {
      this.trabalhoService.buscarPorId(trabalhoId).subscribe({
        next: (t) => {
          if (!this.auth.podeModerar() && t.status !== 'PENDENTE') {
            this.toast.erro('Apenas trabalhos em homologação (pendentes) podem ser editados pelo autor.');
            this.router.navigate(['/trabalhos', t.id]);
            return;
          }
          if (t.orientadorId) {
            this.orientadorVinculado = {
              id: t.orientadorId,
              nome: t.orientadorNome || 'Orientador vinculado',
            };
            this.garantirOrientadorVinculadoNaLista();
          }
          this.form.patchValue({
            titulo: t.titulo,
            tipo: t.tipo,
            ano: t.ano,
            cursoId: t.cursoId,
            orientadorId: t.orientadorId,
            autoresTexto: t.autores.join(', '),
            resumo: t.resumo || '',
            palavrasChave: t.palavrasChave || '',
          });
        },
        error: (err) => {
          this.toast.erro(extrairMensagemErro(err, 'Não foi possível carregar o trabalho para edição.'));
          this.router.navigate(['/meus-envios']);
        },
      });
    }
  }

  private garantirOrientadorVinculadoNaLista(): void {
    const vinculo = this.orientadorVinculado;
    if (!vinculo) return;
    if (!this.orientadores().some((o) => o.id === vinculo.id)) {
      this.orientadores.update((lista) => [
        ...lista,
        { id: vinculo.id, nome: `${vinculo.nome} (inativo)`, email: null, titulacao: null, ativo: false },
      ]);
    }
  }

  aoArrastarSobre(event: DragEvent): void {
    event.preventDefault();
    this.arrastando.set(true);
  }

  aoSoltarArquivo(event: DragEvent): void {
    event.preventDefault();
    this.arrastando.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.validarESelecionarArquivo(file);
  }

  aoSelecionarArquivo(event: Event): void {
    const inputEl = event.target as HTMLInputElement;
    const file = inputEl.files?.[0];
    if (file) this.validarESelecionarArquivo(file);
  }

  private validarESelecionarArquivo(file: File): void {
    if (!file.name.toLowerCase().endsWith('.pdf') && file.type !== 'application/pdf') {
      this.toast.erro('Selecione apenas arquivos no formato PDF.');
      return;
    }
    if (file.size > 100 * 1024 * 1024) {
      this.toast.erro('O arquivo PDF excede o limite máximo de 100 MB.');
      return;
    }
    this.arquivoSelecionado.set(file);
  }

  salvar(): void {
    if (this.form.invalid) return;

    const v = this.form.getRawValue();
    const autores = (v.autoresTexto || '')
      .split(',')
      .map((a) => a.trim())
      .filter((a) => a.length > 0);

    if (autores.length === 0) {
      this.toast.erro('Informe pelo menos um autor.');
      return;
    }

    const payload: TrabalhoRequest = {
      titulo: (v.titulo || '').trim(),
      resumo: (v.resumo || '').trim(),
      palavrasChave: (v.palavrasChave || '').trim(),
      tipo: v.tipo || 'TCC',
      ano: Number(v.ano),
      cursoId: Number(v.cursoId),
      orientadorId: Number(v.orientadorId),
      autores,
    };

    this.enviando.set(true);

    if (this.modoEdicao()) {
      this.trabalhoService.atualizar(Number(this.id()), payload).subscribe({
        next: (res) => {
          this.toast.sucesso('Trabalho atualizado com sucesso.');
          this.router.navigate(['/trabalhos', res.id]);
        },
        error: (err) => {
          this.toast.erro(extrairMensagemErro(err, 'Erro ao atualizar trabalho.'));
          this.enviando.set(false);
        },
      });
      return;
    }

    const arquivo = this.arquivoSelecionado();
    if (!arquivo) return;

    this.progresso.set(0);
    this.trabalhoService.enviar(payload, arquivo).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.progresso.set(Math.round((100 * event.loaded) / event.total));
        } else if (event.type === HttpEventType.Response) {
          this.toast.sucesso('Trabalho enviado com sucesso.');
          this.router.navigate(['/meus-envios']);
        }
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao enviar o PDF.'));
        this.enviando.set(false);
        this.progresso.set(0);
      },
    });
  }

  formatarTamanho(bytes: number): string {
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }
}
