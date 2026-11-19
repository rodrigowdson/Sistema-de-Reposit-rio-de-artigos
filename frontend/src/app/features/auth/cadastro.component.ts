import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { extrairMensagemErro } from '../../core/models/repositorio.models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="max-w-md mx-auto mt-6">
      <div class="catalog-card p-6 md:p-8 rounded-lg space-y-6">
        <div class="border-b border-stone-200 pb-4">
          <span class="text-[11px] font-bold text-amber-900 uppercase tracking-wider">Nova conta</span>
          <h2 class="text-xl font-bold text-stone-900 serif-title mt-1">Cadastro de usuário</h2>
          <p class="text-xs text-stone-600 mt-1">
            Crie seu cadastro para submeter TCCs e artigos para avaliação da biblioteca.
          </p>
        </div>

        <form [formGroup]="form" (ngSubmit)="cadastrar()" class="space-y-4 text-xs">
          <div>
            <label for="nome" class="block font-semibold text-stone-800 mb-1">Nome completo</label>
            <input id="nome" type="text" formControlName="nome" class="input-academic" placeholder="Seu nome completo" />
          </div>

          <div>
            <label for="email" class="block font-semibold text-stone-800 mb-1">E-mail institucional</label>
            <input id="email" type="email" formControlName="email" class="input-academic" placeholder="seu@email.edu.br" />
          </div>

          <div>
            <label for="senha" class="block font-semibold text-stone-800 mb-1">Senha (mínimo 6 caracteres)</label>
            <input id="senha" type="password" formControlName="senha" class="input-academic" placeholder="Crie uma senha" />
          </div>

          <button type="submit" [disabled]="form.invalid || enviando()" class="w-full btn-primary py-2.5 text-sm">
            {{ enviando() ? 'Cadastrando...' : 'Criar conta' }}
          </button>
        </form>

        <p class="text-xs text-center text-stone-600 pt-2 border-t border-stone-200">
          Já possui cadastro?
          <a routerLink="/login" class="text-amber-900 font-semibold hover:underline">Entrar</a>
        </p>
      </div>
    </div>
  `,
})
export class CadastroComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly enviando = signal(false);
  readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    senha: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(100)]],
  });

  cadastrar(): void {
    if (this.form.invalid) return;
    this.enviando.set(true);

    const { nome, email, senha } = this.form.getRawValue();
    this.auth.registrar(nome, email, senha).subscribe({
      next: () => {
        this.toast.sucesso('Cadastro realizado com sucesso.');
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível concluir o cadastro.'));
        this.enviando.set(false);
      },
    });
  }
}
