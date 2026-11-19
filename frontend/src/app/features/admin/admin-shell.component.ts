import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="space-y-6">
      <div class="catalog-card rounded-lg overflow-hidden">
        <div class="px-5 pt-4 pb-3 border-b border-stone-200">
          <span class="text-[11px] font-bold text-amber-900 uppercase tracking-wider">Painel institucional</span>
          <h2 class="text-xl font-bold text-stone-900 serif-title mt-0.5">Administração do Repositório</h2>
        </div>

        <nav class="px-5 flex overflow-x-auto gap-6 text-xs bg-stone-50">
          <a
            routerLink="/admin/moderacao"
            routerLinkActive="active"
            class="admin-subtab py-3 text-stone-600 hover:text-stone-900 transition whitespace-nowrap"
          >
            Moderação
          </a>
          <a
            routerLink="/admin/cursos"
            routerLinkActive="active"
            class="admin-subtab py-3 text-stone-600 hover:text-stone-900 transition whitespace-nowrap"
          >
            Cursos
          </a>
          <a
            routerLink="/admin/orientadores"
            routerLinkActive="active"
            class="admin-subtab py-3 text-stone-600 hover:text-stone-900 transition whitespace-nowrap"
          >
            Orientadores
          </a>
          @if (auth.ehAdmin()) {
            <a
              routerLink="/admin/usuarios"
              routerLinkActive="active"
              class="admin-subtab py-3 text-stone-600 hover:text-stone-900 transition whitespace-nowrap"
            >
              Usuários
            </a>
            <a
              routerLink="/admin/backups"
              routerLinkActive="active"
              class="admin-subtab py-3 text-stone-600 hover:text-stone-900 transition whitespace-nowrap"
            >
              Backups EC2
            </a>
          }
        </nav>
      </div>

      <router-outlet />
    </div>
  `,
})
export class AdminShellComponent {
  readonly auth = inject(AuthService);
}
