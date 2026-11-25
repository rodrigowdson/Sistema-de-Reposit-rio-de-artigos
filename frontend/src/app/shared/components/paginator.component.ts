import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-paginator',
  standalone: true,
  template: `
    @if (totalPages() > 1) {
      <div class="flex items-center justify-center gap-1.5 pt-4 text-xs font-medium">
        <button
          type="button"
          [disabled]="page() <= 0"
          (click)="mudarPagina.emit(page() - 1)"
          class="px-3 py-1.5 border border-stone-300 rounded bg-white text-stone-700 hover:bg-stone-100 disabled:text-stone-400 disabled:hover:bg-white"
        >
          Anterior
        </button>

        @for (p of paginasVisiveis(); track p) {
          <button
            type="button"
            (click)="mudarPagina.emit(p)"
            [class]="
              p === page()
                ? 'px-3 py-1.5 border border-[#1c2430] rounded bg-[#1c2430] text-white'
                : 'px-3 py-1.5 border border-stone-300 rounded bg-white text-stone-700 hover:bg-stone-100'
            "
          >
            {{ p + 1 }}
          </button>
        }

        <button
          type="button"
          [disabled]="page() >= totalPages() - 1"
          (click)="mudarPagina.emit(page() + 1)"
          class="px-3 py-1.5 border border-stone-300 rounded bg-white text-stone-700 hover:bg-stone-100 disabled:text-stone-400 disabled:hover:bg-white"
        >
          Próxima
        </button>
      </div>
    }
  `,
})
export class PaginatorComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly mudarPagina = output<number>();

  paginasVisiveis(): number[] {
    const total = this.totalPages();
    const atual = this.page();
    const fim = Math.min(total - 1, Math.max(atual + 2, 4));
    const inicio = Math.max(0, fim - 4);
    const resultado: number[] = [];
    for (let i = inicio; i <= fim; i++) {
      resultado.push(i);
    }
    return resultado;
  }
}
