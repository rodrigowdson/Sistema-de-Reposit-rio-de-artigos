import { Injectable, signal } from '@angular/core';

export interface ToastMensagem {
  id: number;
  tipo: 'sucesso' | 'erro' | 'info';
  texto: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private proximoId = 1;
  readonly mensagens = signal<ToastMensagem[]>([]);

  sucesso(texto: string): void {
    this.adicionar('sucesso', texto);
  }

  erro(texto: string): void {
    this.adicionar('erro', texto);
  }

  info(texto: string): void {
    this.adicionar('info', texto);
  }

  remover(id: number): void {
    this.mensagens.update((lista) => lista.filter((m) => m.id !== id));
  }

  private adicionar(tipo: 'sucesso' | 'erro' | 'info', texto: string): void {
    const id = this.proximoId++;
    this.mensagens.update((lista) => [...lista, { id, tipo, texto }]);
    setTimeout(() => this.remover(id), 5000);
  }
}
