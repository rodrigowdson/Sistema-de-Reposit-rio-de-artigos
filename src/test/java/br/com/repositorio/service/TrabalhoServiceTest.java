package br.com.repositorio.service;

import br.com.repositorio.backup.BackupConfigRequest;
import br.com.repositorio.backup.BackupProperties;
import br.com.repositorio.backup.BackupService;
import br.com.repositorio.dto.OrientadorResponse;
import br.com.repositorio.dto.TrabalhoRequest;
import br.com.repositorio.dto.TrabalhoResponse;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.ArquivoInfo;
import br.com.repositorio.model.entity.Curso;
import br.com.repositorio.model.entity.Orientador;
import br.com.repositorio.model.entity.Trabalho;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.Perfil;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;
import br.com.repositorio.repository.CursoRepository;
import br.com.repositorio.repository.DownloadLogRepository;
import br.com.repositorio.repository.OrientadorRepository;
import br.com.repositorio.repository.TrabalhoRepository;
import br.com.repositorio.storage.StorageProperties;
import br.com.repositorio.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrabalhoServiceTest {

    @Mock
    private TrabalhoRepository trabalhoRepository;
    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private OrientadorRepository orientadorRepository;
    @Mock
    private DownloadLogRepository downloadLogRepository;
    @Mock
    private StorageService storage;

    @InjectMocks
    private TrabalhoService trabalhoService;

    private Usuario criarUsuario(Long id, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Usuario " + id);
        u.setEmail("u" + id + "@teste.local");
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }

    private Trabalho criarTrabalho(Long id, StatusTrabalho status, Usuario dono, Curso curso, Orientador orientador) {
        Trabalho t = new Trabalho();
        t.setId(id);
        t.setTitulo("Título Original");
        t.setTipo(TipoTrabalho.TCC);
        t.setAno(2024);
        t.setStatus(status);
        t.setCurso(curso);
        t.setOrientador(orientador);
        t.setEnviadoPor(dono);
        t.setAutores(new java.util.LinkedHashSet<>(Set.of("Autor Um")));
        t.setArquivo(new ArquivoInfo("tcc.pdf", "trabalhos/2024/1/abc.pdf", "application/pdf", 1200L, "hash123"));
        return t;
    }

    @Test
    @DisplayName("Autor não pode editar trabalho já publicado")
    void autorNaoPodeEditarTrabalhoPublicado() {
        Usuario aluno = criarUsuario(10L, Perfil.ALUNO);
        Curso curso = new Curso();
        curso.setId(1L);
        curso.setNome("Computação");
        Orientador orientador = new Orientador();
        orientador.setId(1L);
        orientador.setNome("Prof. A");
        orientador.setAtivo(true);

        Trabalho trabalho = criarTrabalho(5L, StatusTrabalho.PUBLICADO, aluno, curso, orientador);
        when(trabalhoRepository.findById(5L)).thenReturn(Optional.of(trabalho));

        TrabalhoRequest req = new TrabalhoRequest(
                "Novo Título", "Resumo", "ia", TipoTrabalho.TCC, 2024, 1L, 1L, Set.of("Autor Um")
        );

        assertThatThrownBy(() -> trabalhoService.atualizar(5L, req, aluno))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pendentes");
    }

    @Test
    @DisplayName("Autor não pode excluir trabalho já publicado")
    void autorNaoPodeExcluirTrabalhoPublicado() {
        Usuario aluno = criarUsuario(10L, Perfil.ALUNO);
        Curso curso = new Curso();
        curso.setId(1L);
        Orientador orientador = new Orientador();
        orientador.setId(1L);

        Trabalho trabalho = criarTrabalho(5L, StatusTrabalho.PUBLICADO, aluno, curso, orientador);
        when(trabalhoRepository.findById(5L)).thenReturn(Optional.of(trabalho));

        assertThatThrownBy(() -> trabalhoService.excluir(5L, aluno))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pendentes");
    }

    @Test
    @DisplayName("Moderador pode editar trabalho publicado e mover arquivo quando ano/curso mudam")
    void moderadorMoveArquivoAoAlterarAnoECurso() {
        Usuario admin = criarUsuario(1L, Perfil.ADMIN);
        Usuario aluno = criarUsuario(10L, Perfil.ALUNO);
        Curso cursoAntigo = new Curso();
        cursoAntigo.setId(1L);
        cursoAntigo.setNome("Computação");
        Curso cursoNovo = new Curso();
        cursoNovo.setId(2L);
        cursoNovo.setNome("Engenharia");
        Orientador orientador = new Orientador();
        orientador.setId(1L);
        orientador.setNome("Prof. A");
        orientador.setAtivo(true);

        Trabalho trabalho = criarTrabalho(5L, StatusTrabalho.PUBLICADO, aluno, cursoAntigo, orientador);
        when(trabalhoRepository.findById(5L)).thenReturn(Optional.of(trabalho));
        when(cursoRepository.findById(2L)).thenReturn(Optional.of(cursoNovo));
        when(trabalhoRepository.saveAndFlush(any(Trabalho.class))).thenAnswer(inv -> inv.getArgument(0));

        TrabalhoRequest req = new TrabalhoRequest(
                "Título Atualizado", "Resumo", "ia", TipoTrabalho.TCC, 2025, 2L, 1L, Set.of("Autor Um")
        );

        TrabalhoResponse res = trabalhoService.atualizar(5L, req, admin);

        assertThat(res.cursoId()).isEqualTo(2L);
        assertThat(res.orientadorId()).isEqualTo(1L);
        assertThat(res.enviadoPorId()).isEqualTo(10L);
        assertThat(trabalho.getArquivo().getChaveStorage()).startsWith("trabalhos/2025/2/");
        verify(storage).mover(eq("trabalhos/2024/1/abc.pdf"), eq(trabalho.getArquivo().getChaveStorage()));
    }

    @Test
    @DisplayName("Listagem pública de orientadores oculta e-mails e inativos")
    void orientadorPublicoOcultaEmailEInativos() {
        Orientador ativo = new Orientador();
        ativo.setId(1L);
        ativo.setNome("Dra. Maria");
        ativo.setEmail("maria@uni.edu.br");
        ativo.setAtivo(true);

        when(orientadorRepository.findByAtivoTrueOrderByNomeAsc()).thenReturn(List.of(ativo));

        OrientadorService service = new OrientadorService(orientadorRepository);
        List<OrientadorResponse> lista = service.listar(false, false);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).email()).isNull();
        assertThat(lista.get(0).nome()).isEqualTo("Dra. Maria");
    }

    @Test
    @DisplayName("BackupService rejeita host 0.0.0.0 e parâmetros SSH começando com hífen")
    void backupServiceRejeitaParametrosSshInvalidos() {
        BackupProperties bp = new BackupProperties();
        StorageProperties sp = new StorageProperties();
        BackupService backupService = new BackupService(bp, sp, null);

        assertThatThrownBy(() -> backupService.atualizarConfiguracao(
                new BackupConfigRequest(false, 60L, "-oProxyCommand", "3.95.1.1", null, "/home/admin/backup")
        )).isInstanceOf(RegraNegocioException.class);

        assertThatThrownBy(() -> backupService.atualizarConfiguracao(
                new BackupConfigRequest(false, 60L, "admin", "0.0.0.0", null, "/home/admin/backup")
        )).isInstanceOf(RegraNegocioException.class);

        assertThatThrownBy(() -> backupService.atualizarConfiguracao(
                new BackupConfigRequest(false, 60L, "admin", "-v", null, "/home/admin/backup")
        )).isInstanceOf(RegraNegocioException.class);
    }
}
