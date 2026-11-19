import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  template: `
    @if (toast.mensagens().length > 0) {
      <div class="space-y-2 mb-5">
        @for (msg of toast.mensagens(); track msg.id) {
          <div
            [class]="classes(msg.tipo)"
            class="px-4 py-2.5 rounded border text-xs flex items-center justify-between gap-3"
          >
            <span>{{ msg.texto }}</span>
            <button
              type="button"
              (click)="toast.remover(msg.id)"
              class="font-semibold underline text-[11px] shrink-0"
            >
              Fechar
            </button>
          </div>
        }
      </div>
    }
  `,
})
export class ToastComponent {
  readonly toast = inject(ToastService);

  classes(tipo: 'sucesso' | 'erro' | 'info'): string {
    switch (tipo) {
      case 'sucesso':
        return 'bg-emerald-50 border-emerald-300 text-emerald-900';
      case 'erro':
        return 'bg-rose-50 border-rose-300 text-rose-900';
      case 'info':
        return 'bg-amber-50 border-amber-300 text-amber-950';
    }
  }
}
