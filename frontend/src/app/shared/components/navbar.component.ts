import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PERFIS_USUARIO } from '../../core/models/repositorio.models';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="bg-[#1c2430] text-stone-200 border-b-4 border-[#78350f]">
      <div class="max-w-6xl mx-auto px-4 py-2.5 flex flex-wrap items-center justify-between gap-3 text-xs border-b border-stone-800">
        <div class="flex items-center gap-2 text-stone-300">
          <span class="font-semibold tracking-wider uppercase text-amber-200">Repositório Institucional</span>
          <span class="text-stone-600">|</span>
          <span class="hidden sm:inline">Sistema de Bibliotecas e Memória Acadêmica</span>
        </div>

        <div class="flex items-center gap-3">
          @if (auth.estaAutenticado()) {
            <span class="text-stone-300">
              <strong class="text-white">{{ auth.usuario()?.nome }}</strong>
              <span class="text-stone-500 mx-1.5">|</span>
              <span class="text-amber-200">{{ rotuloPerfil() }}</span>
            </span>
            <button
              type="button"
              (click)="auth.sair()"
              class="text-stone-300 hover:text-white border border-stone-600 hover:border-stone-400 rounded px-2.5 py-1 transition"
            >
              Sair
            </button>
          } @else {
            <a routerLink="/login" class="text-stone-200 hover:text-white font-medium transition">Entrar</a>
            <a
              routerLink="/cadastro"
              class="border border-stone-600 hover:border-amber-500 text-amber-100 rounded px-2.5 py-1 font-medium transition"
            >
              Cadastrar
            </a>
          }
        </div>
      </div>

      <div class="max-w-6xl mx-auto px-4 py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <a routerLink="/" class="block">
          <h1 class="text-xl md:text-2xl font-bold text-white serif-title tracking-wide">
            Repositório de TCCs e Artigos Acadêmicos
          </h1>
          <p class="text-xs text-stone-400 mt-0.5">
            Catálogo aberto de trabalhos de conclusão de curso, artigos, monografias e dissertações
          </p>
        </a>

        <a
          [routerLink]="auth.estaAutenticado() ? '/enviar' : '/login'"
          class="bg-amber-800 hover:bg-amber-700 text-white px-4 py-2 rounded text-xs font-semibold transition self-start sm:self-auto"
        >
          Submeter trabalho
        </a>
      </div>

      <nav class="max-w-6xl mx-auto px-4 flex overflow-x-auto gap-1 text-xs pt-1">
        <a
          routerLink="/"
          routerLinkActive="active"
          [routerLinkActiveOptions]="{ exact: true }"
          class="tab-link px-4 py-2.5 rounded-t text-stone-300 hover:text-white transition whitespace-nowrap"
        >
          Acervo
        </a>

        @if (auth.estaAutenticado()) {
          <a
            routerLink="/enviar"
            routerLinkActive="active"
            class="tab-link px-4 py-2.5 rounded-t text-stone-300 hover:text-white transition whitespace-nowrap"
          >
            Enviar trabalho
          </a>
          <a
            routerLink="/meus-envios"
            routerLinkActive="active"
            class="tab-link px-4 py-2.5 rounded-t text-stone-300 hover:text-white transition whitespace-nowrap"
          >
            Meus envios
          </a>
        }

        @if (auth.podeModerar()) {
          <a
            routerLink="/admin"
            routerLinkActive="active"
            class="tab-link px-4 py-2.5 rounded-t text-stone-300 hover:text-white transition whitespace-nowrap"
          >
            Administração
          </a>
        }
      </nav>
    </header>
  `,
})
export class NavbarComponent {
  readonly auth = inject(AuthService);

  rotuloPerfil(): string {
    const perfil = this.auth.usuario()?.perfil;
    return PERFIS_USUARIO.find((p) => p.valor === perfil)?.rotulo ?? perfil ?? '';
  }
}
