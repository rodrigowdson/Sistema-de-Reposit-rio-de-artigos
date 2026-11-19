import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Curso, extrairMensagemErro } from '../../../core/models/repositorio.models';
import { CursoService } from '../../../core/services/curso.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-cursos',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="catalog-card p-5 rounded-lg space-y-4 h-fit">
        <div class="border-b border-stone-200 pb-2.5">
          <h3 class="text-base font-bold text-stone-900 serif-title">
            {{ editandoId() ? 'Editar curso' : 'Novo curso' }}
          </h3>
        </div>

        <form [formGroup]="form" (ngSubmit)="salvar()" class="space-y-3 text-xs">
          <div>
            <label for="nomeCurso" class="block font-semibold text-stone-800 mb-1">Nome do curso *</label>
            <input
              id="nomeCurso"
              type="text"
              formControlName="nome"
              placeholder="Ex: Ciência da Computação"
              class="input-academic text-xs"
            />
          </div>

          <div>
            <label for="siglaCurso" class="block font-semibold text-stone-800 mb-1">Sigla</label>
            <input
              id="siglaCurso"
              type="text"
              formControlName="sigla"
              placeholder="Ex: CC"
              class="input-academic text-xs"
            />
          </div>

          <div class="flex items-center gap-2 pt-2">
            <button type="submit" [disabled]="form.invalid || salvando()" class="btn-primary flex-1">
              {{ salvando() ? 'Salvando...' : editandoId() ? 'Atualizar' : 'Cadastrar curso' }}
            </button>
            @if (editandoId()) {
              <button type="button" (click)="cancelarEdicao()" class="btn-secondary">Cancelar</button>
            }
          </div>
        </form>
      </div>

      <div class="lg:col-span-2 catalog-card rounded-lg overflow-hidden">
        <div class="px-4 py-3 bg-stone-100 border-b border-stone-200 flex items-center justify-between text-xs">
          <span class="font-bold text-stone-800">Cursos cadastrados</span>
          <span class="text-stone-500">{{ cursos().length }} registro(s)</span>
        </div>

        @if (carregando()) {
          <div class="p-6 text-center text-xs text-stone-500">Carregando cursos...</div>
        } @else if (cursos().length === 0) {
          <div class="p-6 text-center text-xs text-stone-500">Nenhum curso cadastrado.</div>
        } @else {
          <table class="w-full text-left text-xs">
            <thead class="border-b border-stone-200 text-stone-600 uppercase text-[11px] bg-stone-50">
              <tr>
                <th class="p-3">Nome</th>
                <th class="p-3">Sigla</th>
                <th class="p-3 text-right">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-stone-200">
              @for (curso of cursos(); track curso.id) {
                <tr class="hover:bg-stone-50/70">
                  <td class="p-3 font-semibold text-stone-900">{{ curso.nome }}</td>
                  <td class="p-3 text-stone-600 font-mono">{{ curso.sigla || '-' }}</td>
                  <td class="p-3 text-right space-x-3 whitespace-nowrap">
                    <button
                      type="button"
                      (click)="iniciarEdicao(curso)"
                      class="text-amber-900 font-semibold hover:underline"
                    >
                      Editar
                    </button>
                    <button
                      type="button"
                      (click)="excluir(curso)"
                      class="text-rose-700 hover:underline"
                    >
                      Excluir
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </div>
  `,
})
export class CursosComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly cursoService = inject(CursoService);
  private readonly toast = inject(ToastService);

  readonly cursos = signal<Curso[]>([]);
  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly editandoId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    sigla: ['', [Validators.maxLength(20)]],
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.cursoService.listar().subscribe({
      next: (lista) => {
        this.cursos.set(lista);
        this.carregando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao listar cursos.'));
        this.carregando.set(false);
      },
    });
  }

  salvar(): void {
    if (this.form.invalid) return;
    this.salvando.set(true);

    const payload = this.form.getRawValue();
    const id = this.editandoId();
    const req$ = id ? this.cursoService.atualizar(id, payload) : this.cursoService.criar(payload);

    req$.subscribe({
      next: () => {
        this.toast.sucesso(id ? 'Curso atualizado.' : 'Curso cadastrado.');
        this.cancelarEdicao();
        this.salvando.set(false);
        this.carregar();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao salvar curso.'));
        this.salvando.set(false);
      },
    });
  }

  iniciarEdicao(curso: Curso): void {
    this.editandoId.set(curso.id);
    this.form.setValue({ nome: curso.nome, sigla: curso.sigla || '' });
  }

  cancelarEdicao(): void {
    this.editandoId.set(null);
    this.form.reset({ nome: '', sigla: '' });
  }

  excluir(curso: Curso): void {
    if (!confirm(`Excluir o curso "${curso.nome}"?`)) return;
    this.cursoService.excluir(curso.id).subscribe({
      next: () => {
        this.toast.sucesso('Curso excluído.');
        this.carregar();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível excluir o curso.'));
      },
    });
  }
}
