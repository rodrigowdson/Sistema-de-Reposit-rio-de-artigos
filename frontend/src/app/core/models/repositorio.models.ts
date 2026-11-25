export type Perfil = 'ADMIN' | 'BIBLIOTECARIO' | 'PROFESSOR' | 'ALUNO';
export type StatusTrabalho = 'PENDENTE' | 'PUBLICADO' | 'ARQUIVADO';
export type TipoTrabalho = 'TCC' | 'ARTIGO' | 'MONOGRAFIA' | 'DISSERTACAO' | 'TESE';

export const TIPOS_TRABALHO: { valor: TipoTrabalho; rotulo: string }[] = [
  { valor: 'TCC', rotulo: 'TCC' },
  { valor: 'ARTIGO', rotulo: 'Artigo Científico' },
  { valor: 'MONOGRAFIA', rotulo: 'Monografia' },
  { valor: 'DISSERTACAO', rotulo: 'Dissertação' },
  { valor: 'TESE', rotulo: 'Tese' },
];

export const PERFIS_USUARIO: { valor: Perfil; rotulo: string }[] = [
  { valor: 'ALUNO', rotulo: 'Aluno' },
  { valor: 'PROFESSOR', rotulo: 'Professor' },
  { valor: 'BIBLIOTECARIO', rotulo: 'Bibliotecário' },
  { valor: 'ADMIN', rotulo: 'Administrador' },
];

export interface Curso {
  id: number;
  nome: string;
  sigla: string | null;
}

export interface CursoRequest {
  nome: string;
  sigla?: string | null;
}

export interface Orientador {
  id: number;
  nome: string;
  email: string | null;
  titulacao: string | null;
  ativo: boolean;
}

export interface OrientadorRequest {
  nome: string;
  email: string;
  titulacao?: string | null;
}

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  ativo: boolean;
  criadoEm: string;
}

export interface AuthResponse {
  token: string;
  tipo: string;
  expiraEmSegundos: number;
  usuario: Usuario;
}

export interface Trabalho {
  id: number;
  titulo: string;
  resumo: string | null;
  palavrasChave: string | null;
  tipo: TipoTrabalho;
  ano: number;
  status: StatusTrabalho;
  cursoId: number | null;
  cursoNome: string | null;
  orientadorId: number | null;
  orientadorNome: string | null;
  enviadoPorId: number | null;
  autores: string[];
  arquivoNomeOriginal: string | null;
  arquivoHashSha256: string | null;
  totalDownloads: number;
  criadoEm: string;
  atualizadoEm: string;
}

export interface TrabalhoRequest {
  titulo: string;
  resumo: string;
  palavrasChave: string;
  tipo: TipoTrabalho;
  ano: number;
  cursoId: number;
  orientadorId: number;
  autores: string[];
}

export interface TrabalhoFiltro {
  cursoId?: number | null;
  orientadorId?: number | null;
  ano?: number | null;
  tipo?: TipoTrabalho | null;
  status?: StatusTrabalho | null;
  termo?: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface BackupExecucao {
  id: string;
  tipo: 'BACKUP' | 'RESTAURACAO';
  origem: 'MANUAL' | 'AGENDADO';
  status: 'SUCESSO' | 'ERRO';
  iniciadoEm: string;
  finalizadoEm: string;
  duracaoMs: number;
  destino: string;
  mensagem: string;
  saida: string;
}

export interface BackupStatus {
  habilitado: boolean;
  intervaloMinutos: number;
  emExecucao: boolean;
  ec2User: string;
  ec2Host: string;
  ec2Dir: string;
  chaveConfigurada: boolean;
  diretorioLocal: string;
  proximoAgendamento: string | null;
  ultimaExecucao: BackupExecucao | null;
  totalExecucoesNoHistorico: number;
}

export interface BackupConfigRequest {
  habilitado?: boolean;
  intervaloMinutos?: number;
  ec2User?: string;
  ec2Host?: string;
  ec2Key?: string;
  ec2Dir?: string;
}

export function rotuloTipo(tipo: TipoTrabalho | string | null | undefined): string {
  const encontrado = TIPOS_TRABALHO.find((t) => t.valor === tipo);
  return encontrado ? encontrado.rotulo : tipo || '';
}

export function extrairMensagemErro(err: unknown, padrao = 'Não foi possível concluir a operação.'): string {
  if (typeof err === 'object' && err !== null) {
    const httpErr = err as { error?: { mensagem?: string; detail?: string; campos?: { campo: string; mensagem: string }[] } };
    if (httpErr.error?.campos && httpErr.error.campos.length > 0) {
      return httpErr.error.campos.map((c) => `${c.campo}: ${c.mensagem}`).join(' | ');
    }
    if (httpErr.error?.mensagem) return httpErr.error.mensagem;
    if (httpErr.error?.detail) return httpErr.error.detail;
  }
  return padrao;
}
