import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar.component';
import { ToastComponent } from './shared/components/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, ToastComponent],
  template: `
    <div class="min-h-screen flex flex-col">
      <app-navbar />

      <main class="max-w-6xl mx-auto px-4 py-6 flex-1 w-full">
        <app-toast />
        <router-outlet />
      </main>

      <footer class="bg-stone-200/80 border-t border-stone-300 py-4 text-xs text-stone-600 mt-10">
        <div class="max-w-6xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Repositório Institucional de TCCs e Artigos Acadêmicos</span>
          <span class="text-stone-500">Acesso aberto à produção acadêmica</span>
        </div>
      </footer>
    </div>
  `,
})
export class AppComponent {}
