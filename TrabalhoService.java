package br.com.repositorio.service;

import br.com.repositorio.dto.ArquivoDownload;
import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.dto.TrabalhoRequest;
import br.com.repositorio.dto.TrabalhoResponse;
import br.com.repositorio.exception.ArmazenamentoException;
import br.com.repositorio.exception.RecursoNaoEncontradoException;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.*;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.repository.*;
import br.com.repositorio.repository.spec.TrabalhoSpecs;
import br.com.repositorio.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrabalhoService {

    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final TrabalhoRepository trabalhoRepository;
    private final CursoRepository cursoRepository;
    private final OrientadorRepository orientadorRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final StorageService storage;

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    @Transactional
    public TrabalhoResponse enviar(TrabalhoRequest dados, MultipartFile arquivo, Usuario usuario) {
        validarPdf(arquivo);

        String hash = calcularSha256(arquivo);
        if (trabalhoRepository.existsByArquivoHashSha256(hash)) {
            throw new RegraNegocioException("Este arquivo já foi enviado anteriormente.");
        }

        Curso curso = buscarCurso(dados.cursoId());
        Orientador orientador = buscarOrientador(dados.orientadorId());

        String chave = gerarChave(dados.ano(), curso.getId());
        storage.salvar(chave, arquivo);
        limparArquivoQuando(chave, false); // se a transação der rollback, apaga o PDF órfão

        Trabalho t = new Trabalho();
        preencher(t, dados, curso, orientador);
        t.setArquivo(new ArquivoInfo(
                nomeSeguro(arquivo.getOriginalFilename()), chave, "application/pdf", arquivo.getSize(), hash));
        t.setEnviadoPor(usuario);
        t.setStatus(ehModerador(usuario) ? StatusTrabalho.PUBLICADO : StatusTrabalho.PENDENTE);

        return TrabalhoResponse.from(trabalhoRepository.save(t));
    }

    // ------------------------------------------------------------------
    // Busca e consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<TrabalhoResponse> buscar(TrabalhoFiltro filtro, Pageable pageable, Usuario usuario) {
        boolean apenasPublicados = !ehModerador(usuario);
        return trabalhoRepository
                .findAll(TrabalhoSpecs.comFiltro(filtro, apenasPublicados), pageable)
                .map(TrabalhoResponse::from);
    }

    @Transactional(readOnly = true)
    public TrabalhoResponse buscarPorId(Long id, Usuario usuario) {
        return TrabalhoResponse.from(obterVisivel(id, usuario));
    }

    @Transactional(readOnly = true)
    public List<Integer> listarAnos() {
        return trabalhoRepository.listarAnosPublicados();
    }

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    @Transactional
    public ArquivoDownload baixar(Long id, Usuario usuario, String ipOrigem) {
        Trabalho t = obterVisivel(id, usuario);
        ArquivoInfo a = t.getArquivo();

        Resource recurso = storage.carregar(a.getChaveStorage());

        trabalhoRepository.incrementarDownloads(id);
        downloadLogRepository.save(new DownloadLog(t, usuario, ipOrigem));

        return new ArquivoDownload(recurso, a.getNomeOriginal(), a.getContentType(), a.getTamanhoBytes());
    }

    // ------------------------------------------------------------------
    // Edição, moderação e exclusão
    // ------------------------------------------------------------------

    @Transactional
    public TrabalhoResponse atualizar(Long id, TrabalhoRequest dados, Usuario usuario) {
        Trabalho t = obter(id);
        validarPermissaoEdicao(t, usuario);
        preencher(t, dados, buscarCurso(dados.cursoId()), buscarOrientador(dados.orientadorId()));
        return TrabalhoResponse.from(t);
    }

    @Transactional
    public TrabalhoResponse alterarStatus(Long id, StatusTrabalho novoStatus, Usuario usuario) {
        if (!ehModerador(usuario)) {
            throw new AccessDeniedException("Apenas administradores e bibliotecários podem alterar o status.");
        }
        Trabalho t = obter(id);
        t.setStatus(novoStatus);
        return TrabalhoResponse.from(t);
    }

    @Transactional
    public void excluir(Long id, Usuario usuario) {
        Trabalho t = obter(id);
        validarPermissaoEdicao(t, usuario);
        String chave = t.getArquivo().getChaveStorage();
        trabalhoRepository.delete(t);
        limparArquivoQuando(chave, true); // só apaga o PDF depois que o commit der certo
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Trabalho obter(Long id) {
        return trabalhoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Trabalho", id));
    }

    /** Trabalhos não publicados só aparecem para moderadores e para quem os enviou. */
    private Trabalho obterVisivel(Long id, Usuario usuario) {
        Trabalho t = obter(id);
        if (t.getStatus() != StatusTrabalho.PUBLICADO && !ehModerador(usuario) && !ehDono(t, usuario)) {
            throw new RecursoNaoEncontradoException("Trabalho", id);
        }
        return t;
    }

    private void validarPermissaoEdicao(Trabalho t, Usuario usuario) {
        if (!ehModerador(usuario) && !ehDono(t, usuario)) {
            throw new AccessDeniedException("Você não tem permissão para alterar este trabalho.");
        }
    }

    private boolean ehModerador(Usuario u) {
        return u != null && u.getPerfil() != null && u.getPerfil().podeModerar();
    }

    private boolean ehDono(Trabalho t, Usuario u) {
        return u != null && t.getEnviadoPor() != null && Objects.equals(t.getEnviadoPor().getId(), u.getId());
    }

    private Curso buscarCurso(Long id) {
        return cursoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Curso", id));
    }

    private Orientador buscarOrientador(Long id) {
        Orientador o = orientadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orientador", id));
        if (!o.isAtivo()) {
            throw new RegraNegocioException("O orientador selecionado está inativo.");
        }
        return o;
    }

    private void preencher(Trabalho t, TrabalhoRequest d, Curso curso, Orientador orientador) {
        t.setTitulo(d.titulo().trim());
        t.setResumo(d.resumo());
        t.setPalavrasChave(d.palavrasChave());
        t.setTipo(d.tipo());
        t.setAno(d.ano());
        t.setCurso(curso);
        t.setOrientador(orientador);
        t.getAutores().clear();
        d.autores().forEach(a -> t.getAutores().add(a.trim()));
    }

    /** Organiza o storage por ano/curso: trabalhos/2025/3/uuid.pdf */
    private String gerarChave(Integer ano, Long cursoId) {
        return "trabalhos/%d/%d/%s.pdf".formatted(ano, cursoId, UUID.randomUUID());
    }

    /** Confere o tamanho e a assinatura "%PDF-" (não confia só na extensão nem no Content-Type). */
    private void validarPdf(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraNegocioException("Envie um arquivo PDF.");
        }
        try (InputStream in = arquivo.getInputStream()) {
            byte[] inicio = in.readNBytes(ASSINATURA_PDF.length);
            if (!Arrays.equals(inicio, ASSINATURA_PDF)) {
                throw new RegraNegocioException("O arquivo enviado não é um PDF válido.");
            }
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private String calcularSha256(MultipartFile arquivo) {
        try (InputStream in = arquivo.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                md.update(buffer, 0, lidos);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ArmazenamentoException("Falha ao calcular o hash do arquivo.", e);
        }
    }

    private String nomeSeguro(String original) {
        String nome = StringUtils.cleanPath(original == null ? "trabalho.pdf" : original);
        nome = nome.substring(nome.lastIndexOf('/') + 1);
        if (nome.isBlank()) nome = "trabalho.pdf";
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    /**
     * Mantém banco e storage consistentes:
     * quandoCommit=false → apaga o arquivo se a transação der rollback (upload);
     * quandoCommit=true  → apaga o arquivo somente após o commit (exclusão).
     */
    private void limparArquivoQuando(String chave, boolean quandoCommit) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean commitou = status == STATUS_COMMITTED;
                if (commitou == quandoCommit) {
                    try {
                        storage.remover(chave);
                    } catch (RuntimeException e) {
                        log.warn("Não foi possível remover o arquivo {} do storage", chave, e);
                    }
                }
            }
        });
    }
}
