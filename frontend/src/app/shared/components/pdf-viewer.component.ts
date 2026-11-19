import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TrabalhoService } from '../../core/services/trabalho.service';

@Component({
  selector: 'app-pdf-viewer',
  standalone: true,
  template: `
    <div class="catalog-card rounded-lg overflow-hidden">
      <div class="px-4 py-2.5 bg-stone-100 border-b border-stone-200 flex items-center justify-between text-xs">
        <span class="font-semibold text-stone-700">Visualização do documento PDF</span>
        @if (rawUrl) {
          <a
            [href]="rawUrl"
            target="_blank"
            rel="noopener"
            class="text-amber-900 font-semibold hover:underline"
          >
            Abrir em nova aba
          </a>
        }
      </div>

      @if (carregando()) {
        <div class="p-10 text-center text-xs text-stone-500">Carregando documento...</div>
      } @else if (erro()) {
        <div class="p-6 text-center text-xs text-rose-700">{{ erro() }}</div>
      } @else if (pdfUrl()) {
        <iframe
          [src]="pdfUrl()"
          title="Visualizador de PDF"
          class="w-full h-[700px] border-0 bg-stone-50"
        ></iframe>
      }
    </div>
  `,
})
export class PdfViewerComponent implements OnDestroy {
  private readonly trabalhoService = inject(TrabalhoService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly trabalhoId = input.required<number>();

  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  rawUrl: string | null = null;

  constructor() {
    effect(() => {
      const id = this.trabalhoId();
      if (id) {
        this.carregarPdf(id);
      }
    });
  }

  private carregarPdf(id: number): void {
    this.revogarUrl();
    this.carregando.set(true);
    this.erro.set(null);

    this.trabalhoService.obterPdfBlob(id, true).subscribe({
      next: (blob) => {
        const pdfBlob = new Blob([blob], { type: 'application/pdf' });
        this.rawUrl = URL.createObjectURL(pdfBlob);
        this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.rawUrl));
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar a pré-visualização deste PDF.');
        this.carregando.set(false);
      },
    });
  }

  private revogarUrl(): void {
    if (this.rawUrl) {
      URL.revokeObjectURL(this.rawUrl);
      this.rawUrl = null;
    }
    this.pdfUrl.set(null);
  }

  ngOnDestroy(): void {
    this.revogarUrl();
  }
}
