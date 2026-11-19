import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  PERFIS_USUARIO,
  Perfil,
  Usuario,
  extrairMensagemErro,
} from '../../../core/models/repositorio.models';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuarioService } from '../../../core/services/usuario.service';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="catalog-card rounded-lg overflow-x-auto">
      <div class="px-4 py-3 bg-stone-100 border-b border-stone-200 flex items-center justify-between text-xs">
        <span class="font-bold text-stone-800">Gestão de usuários e perfis</span>
        <span class="text-stone-500">{{ usuarios().length }} conta(s)</span>
      </div>

      @if (carregando()) {
        <div class="p-6 text-center text-xs text-stone-500">Carregando usuários...</div>
      } @else {
        <table class="w-full text-left text-xs">
          <thead class="border-b border-stone-200 text-stone-600 uppercase text-[11px] bg-stone-50">
            <tr>
              <th class="p-3">Nome</th>
              <th class="p-3">E-mail</th>
              <th class="p-3">Perfil</th>
              <th class="p-3">Situação</th>
              <th class="p-3">Cadastro</th>
              <th class="p-3 text-right">Ações</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-stone-200">
            @for (u of usuarios(); track u.id) {
              <tr class="hover:bg-stone-50/70">
                <td class="p-3 font-semibold text-stone-900">
                  {{ u.nome }}
                  @if (u.id === auth.usuario()?.id) {
                    <span class="text-[10px] font-normal text-stone-500 ml-1">(você)</span>
                  }
                </td>
                <td class="p-3 text-stone-600">{{ u.email }}</td>
                <td class="p-3">
                  <select
                    [ngModel]="u.perfil"
                    (ngModelChange)="alterarPerfil(u, $event)"
                    [disabled]="u.id === auth.usuario()?.id"
                    class="bg-stone-50 border border-stone-300 rounded px-2 py-1 text-xs text-stone-800 disabled:opacity-60"
                  >
                    @for (p of perfis; track p.valor) {
                      <option [value]="p.valor">{{ p.rotulo }}</option>
                    }
                  </select>
                </td>
                <td class="p-3 whitespace-nowrap">
                  <span
                    [class]="
                      u.ativo
                        ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                        : 'bg-stone-100 text-stone-600 border-stone-300'
                    "
                    class="px-2 py-0.5 rounded border text-[11px] font-semibold"
                  >
                    {{ u.ativo ? 'Ativo' : 'Desativado' }}
                  </span>
                </td>
                <td class="p-3 text-stone-600 whitespace-nowrap">
                  {{ u.criadoEm | date: 'dd/MM/yyyy' }}
                </td>
                <td class="p-3 text-right whitespace-nowrap">
                  <button
                    type="button"
                    [disabled]="u.id === auth.usuario()?.id"
                    (click)="alternarAtivo(u)"
                    class="text-stone-700 font-semibold hover:underline disabled:opacity-40"
                  >
                    {{ u.ativo ? 'Desativar' : 'Ativar' }}
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class UsuariosComponent implements OnInit {
  private readonly usuarioService = inject(UsuarioService);
  private readonly toast = inject(ToastService);
  readonly auth = inject(AuthService);

  readonly perfis = PERFIS_USUARIO;
  readonly usuarios = signal<Usuario[]>([]);
  readonly carregando = signal(true);

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.usuarioService.listar().subscribe({
      next: (lista) => {
        this.usuarios.set(lista);
        this.carregando.set(false);
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Erro ao carregar usuários.'));
        this.carregando.set(false);
      },
    });
  }

  alterarPerfil(u: Usuario, novoPerfil: Perfil): void {
    this.usuarioService.alterarPerfil(u.id, novoPerfil).subscribe({
      next: () => {
        this.toast.sucesso(`Perfil de ${u.nome} atualizado.`);
        this.carregar();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível alterar o perfil.'));
        this.carregar();
      },
    });
  }

  alternarAtivo(u: Usuario): void {
    this.usuarioService.alterarAtivo(u.id, !u.ativo).subscribe({
      next: () => {
        this.toast.sucesso(u.ativo ? 'Conta desativada.' : 'Conta ativada.');
        this.carregar();
      },
      error: (err) => {
        this.toast.erro(extrairMensagemErro(err, 'Não foi possível atualizar a conta.'));
      },
    });
  }
}
