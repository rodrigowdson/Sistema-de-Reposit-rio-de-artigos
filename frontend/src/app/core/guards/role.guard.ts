import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Perfil } from '../models/repositorio.models';
import { AuthService } from '../services/auth.service';

export function roleGuard(perfisPermitidos: Perfil[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const perfil = auth.usuario()?.perfil;

    if (perfil && perfisPermitidos.includes(perfil)) {
      return true;
    }

    return router.createUrlTree(['/']);
  };
}
