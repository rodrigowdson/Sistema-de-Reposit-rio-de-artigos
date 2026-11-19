import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/acervo/acervo.component').then((m) => m.AcervoComponent),
  },
  {
    path: 'trabalhos/:id',
    loadComponent: () =>
      import('./features/trabalho-detalhe/trabalho-detalhe.component').then(
        (m) => m.TrabalhoDetalheComponent
      ),
  },
  {
    path: 'trabalhos/:id/editar',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/enviar-trabalho/enviar-trabalho.component').then(
        (m) => m.EnviarTrabalhoComponent
      ),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'cadastro',
    loadComponent: () =>
      import('./features/auth/cadastro.component').then((m) => m.CadastroComponent),
  },
  {
    path: 'enviar',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/enviar-trabalho/enviar-trabalho.component').then(
        (m) => m.EnviarTrabalhoComponent
      ),
  },
  {
    path: 'meus-envios',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/meus-envios/meus-envios.component').then(
        (m) => m.MeusEnviosComponent
      ),
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard(['ADMIN', 'BIBLIOTECARIO'])],
    loadComponent: () =>
      import('./features/admin/admin-shell.component').then((m) => m.AdminShellComponent),
    children: [
      { path: '', redirectTo: 'moderacao', pathMatch: 'full' },
      {
        path: 'moderacao',
        loadComponent: () =>
          import('./features/admin/moderacao/moderacao.component').then(
            (m) => m.ModeracaoComponent
          ),
      },
      {
        path: 'cursos',
        loadComponent: () =>
          import('./features/admin/cursos/cursos.component').then((m) => m.CursosComponent),
      },
      {
        path: 'orientadores',
        loadComponent: () =>
          import('./features/admin/orientadores/orientadores.component').then(
            (m) => m.OrientadoresComponent
          ),
      },
      {
        path: 'usuarios',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/usuarios/usuarios.component').then(
            (m) => m.UsuariosComponent
          ),
      },
      {
        path: 'backups',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/backups/backups.component').then(
            (m) => m.BackupsComponent
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
