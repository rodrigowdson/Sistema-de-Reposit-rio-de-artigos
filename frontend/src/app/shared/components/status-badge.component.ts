import { Component, input } from '@angular/core';
import { StatusTrabalho } from '../../core/models/repositorio.models';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span [class]="classes()" class="inline-block px-2 py-0.5 rounded text-[11px] font-semibold border">
      {{ rotulo() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<StatusTrabalho>();

  rotulo(): string {
    switch (this.status()) {
      case 'PUBLICADO':
        return 'Publicado';
      case 'PENDENTE':
        return 'Em homologação';
      case 'ARQUIVADO':
        return 'Arquivado';
    }
  }

  classes(): string {
    switch (this.status()) {
      case 'PUBLICADO':
        return 'bg-emerald-50 text-emerald-800 border-emerald-200';
      case 'PENDENTE':
        return 'bg-amber-50 text-amber-900 border-amber-200';
      case 'ARQUIVADO':
        return 'bg-stone-100 text-stone-600 border-stone-300';
    }
  }
}
