import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { extrairMensagemErro } from '../../core/models/repositorio.models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="max-w-md mx-auto mt-6">
      <div class="catalog-card p-6 md:p-8 rounded-lg space-y-6">
        <div class="border-b border-stone-200 pb-4">
          <span class="text-[11px] font-bold text-amber-900 uppercase tracking-wider">Acesso ao sistema</span>
          <h2 class="text-xl font-bold text-stone-900 serif-title mt-1">Entrar no repositório</h2>
          <p class="text-xs text-stone-600 mt-1">
            Acesse sua conta para enviar trabalhos, acompanhar homologações ou administrar o acervo.
          </p>
        </div>

        <form [formGroup]="form" (ngSubmit)="entrar()" class="space-y-4 text-xs">
          <div>
            <label for="email" class="block font-semibold text-stone-800 mb-1">E-mail</label>
            <input id="email" type="email" formControlName="email" class="input-academic" placeholder="seu@email.edu.br" />
          </div>

          <div>
            <label for="senha" class="block font-semibold text-stone-800 mb-1">Senha</label>
            <input id="senha" type="password" formControlName="senha" class="input-academic" placeholder="Digite sua senha" />
          </div>

          <button type="submit" [disabled]="form.invalid || enviando()" class="w-full btn-primary py-2.5 text-sm">
            {{ enviando() ? 'Entrando...' : 'Entrar' }}
          </button>
        </form>

        <p class="text-xs text-center text-stone-600 pt-2 border-t border-stone-200">
          Ainda não possui conta?
          <a routerLink="/cadastro" class="text-amber-900 font-semibold hover:underline">Criar cadastro</a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  readonly enviando = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', [Validators.required]],
  });

  entrar(): void {
    if (this.form.invalid) return;
    this.enviando.set(true);

    const { email, senha } = this.form.getRawValue();
    this.auth.login(email, senha).subscribe({
      next: () => {
        this.toast.sucesso('Login realizado com sucesso.');
        const raw = this.route.snapshot.queryParamMap.get('redirect') || '/';
        const redirect = raw.startsWith('/') && !raw.startsWith('//') ? raw : '/';
        this.router.navigateByUrl(redirect);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'E-mail ou senha inválidos.'));
        this.enviando.set(false);
      },
    });
  }
}
