import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Orientador, extrairMensagemErro } from '../../../core/models/repositorio.models';
import { OrientadorService } from '../../../core/services/orientador.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-orientadores',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="catalog-card p-5 rounded-lg space-y-4 h-fit">
        <div class="border-b border-stone-200 pb-2.5">
          <h3 class="text-base font-bold text-stone-900 serif-title">
            {{ editandoId() ? 'Editar orientador' : 'Novo orientador' }}
          </h3>
        </div>

        <form [formGroup]="form" (ngSubmit)="salvar()" class="space-y-3 text-xs">
          <div>
            <label for="nomeOri" class="block font-semibold text-stone-800 mb-1">Nome completo *</label>
            <input
              id="nomeOri"
              type="text"
              formControlName="nome"
              placeholder="Ex: Roberto Silva"
              class="input-academic text-xs"
            />
          </div>

          <div>
            <label for="emailOri" class="block font-semibold text-stone-800 mb-1">E-mail *</label>
            <input
              id="emailOri"
              type="email"
              formControlName="email"
              placeholder="roberto.silva@universidade.edu.br"
              class="input-academic text-xs"
            />
          </div>

          <div>
            <label for="titulacaoOri" class="block font-semibold text-stone-800 mb-1">Titulação</label>
            <input
              id="titulacaoOri"
              type="text"
              formControlName="titulacao"
              placeholder="Ex: Prof. Dr."
              class="input-academic text-xs"
            />
          </div>

          <div class="flex items-center gap-2 pt-2">
            <button type="submit" [disabled]="form.invalid || salvando()" class="btn-primary flex-1">
              {{ salvando() ? 'Salvando...' : editandoId() ? 'Atualizar' : 'Cadastrar orientador' }}
            </button>
            @if (editandoId()) {
              <button type="button" (click)="cancelarEdicao()" class="btn-secondary">Cancelar</button>
            }
          </div>
        </form>
      </div>

      <div class="lg:col-span-2 catalog-card rounded-lg overflow-x-auto">
        <div class="px-4 py-3 bg-stone-100 border-b border-stone-200 flex items-center justify-between text-xs">
          <span class="font-bold text-stone-800">Orientadores cadastrados</span>
          <span class="text-stone-500">{{ orientadores().length }} registro(s)</span>
        </div>

        @if (carregando()) {
          <div class="p-6 text-center text-xs text-stone-500">Carregando orientadores...</div>
        } @else if (orientadores().length === 0) {
          <div class="p-6 text-center text-xs text-stone-500">Nenhum orientador cadastrado.</div>
        } @else {
          <table class="w-full text-left text-xs">
            <thead class="border-b border-stone-200 text-stone-600 uppercase text-[11px] bg-stone-50">
              <tr>
                <th class="p-3">Orientador</th>
                <th class="p-3">E-mail</th>
                <th class="p-3">Situação</th>
                <th class="p-3 text-right">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-stone-200">
              @for (ori of orientadores(); track ori.id) {
                <tr class="hover:bg-stone-50/70">
                  <td class="p-3">
                    <div class="font-semibold text-stone-900">
                      {{ ori.titulacao ? ori.titulacao + ' ' : '' }}{{ ori.nome }}
                    </div>
                  </td>
                  <td class="p-3 text-stone-600">{{ ori.email }}</td>
                  <td class="p-3 whitespace-nowrap">
                    <span
                      [class]="
                        ori.ativo
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                          : 'bg-stone-100 text-stone-600 border-stone-300'
                      "
                      class="px-2 py-0.5 rounded border text-[11px] font-semibold"
                    >
                      {{ ori.ativo ? 'Ativo' : 'Inativo' }}
                    </span>
                  </td>
                  <td class="p-3 text-right space-x-3 whitespace-nowrap">
                    <button
                      type="button"
                      (click)="iniciarEdicao(ori)"
                      class="text-amber-900 font-semibold hover:underline"
                    >
                      Editar
                    </button>
                    <button
                      type="button"
                      (click)="alternarAtivo(ori)"
                      class="text-stone-700 hover:underline"
                    >
                      {{ ori.ativo ? 'Inativar' : 'Ativar' }}
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
export class OrientadoresComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly orientadorService = inject(OrientadorService);
  private readonly toast = inject(ToastService);

  readonly orientadores = signal<Orientador[]>([]);
  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly editandoId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    titulacao: ['', [Validators.maxLength(50)]],
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.orientadorService.listar(false).subscribe({
      next: (lista) => {
        this.orientadores.set(lista);
        this.carregando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao listar orientadores.'));
        this.carregando.set(false);
      },
    });
  }

  salvar(): void {
    if (this.form.invalid) return;
    this.salvando.set(true);

    const payload = this.form.getRawValue();
    const id = this.editandoId();
    const req$ = id
      ? this.orientadorService.atualizar(id, payload)
      : this.orientadorService.criar(payload);

    req$.subscribe({
      next: () => {
        this.toast.sucesso(id ? 'Orientador atualizado.' : 'Orientador cadastrado.');
        this.cancelarEdicao();
        this.salvando.set(false);
        this.carregar();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao salvar orientador.'));
        this.salvando.set(false);
      },
    });
  }

  iniciarEdicao(ori: Orientador): void {
    this.editandoId.set(ori.id);
    this.form.setValue({
      nome: ori.nome,
      email: ori.email,
      titulacao: ori.titulacao || '',
    });
  }

  cancelarEdicao(): void {
    this.editandoId.set(null);
    this.form.reset({ nome: '', email: '', titulacao: '' });
  }

  alternarAtivo(ori: Orientador): void {
    this.orientadorService.alterarAtivo(ori.id, !ori.ativo).subscribe({
      next: () => {
        this.toast.sucesso(ori.ativo ? 'Orientador inativado.' : 'Orientador ativado.');
        this.carregar();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao atualizar situação do orientador.'));
      },
    });
  }
}
