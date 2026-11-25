package br.com.repositorio.service;

import br.com.repositorio.dto.ArquivoDownload;
import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.dto.TrabalhoRequest;
import br.com.repositorio.dto.TrabalhoResponse;
import br.com.repositorio.exception.ArmazenamentoException;
import br.com.repositorio.exception.RecursoNaoEncontradoException;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.ArquivoInfo;
import br.com.repositorio.model.entity.Curso;
import br.com.repositorio.model.entity.DownloadLog;
import br.com.repositorio.model.entity.Orientador;
import br.com.repositorio.model.entity.Trabalho;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.repository.CursoRepository;
import br.com.repositorio.repository.DownloadLogRepository;
import br.com.repositorio.repository.OrientadorRepository;
import br.com.repositorio.repository.TrabalhoRepository;
import br.com.repositorio.repository.spec.TrabalhoSpecs;
import br.com.repositorio.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TrabalhoService {

    private static final Logger log = LoggerFactory.getLogger(TrabalhoService.class);
    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final TrabalhoRepository trabalhoRepository;
    private final CursoRepository cursoRepository;
    private final OrientadorRepository orientadorRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final StorageService storage;

    public TrabalhoService(
            TrabalhoRepository trabalhoRepository,
            CursoRepository cursoRepository,
            OrientadorRepository orientadorRepository,
            DownloadLogRepository downloadLogRepository,
            StorageService storage
    ) {
        this.trabalhoRepository = trabalhoRepository;
        this.cursoRepository = cursoRepository;
        this.orientadorRepository = orientadorRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.storage = storage;
    }

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
        limparArquivoQuando(chave, false);

        Trabalho t = new Trabalho();
        preencher(t, dados, curso, orientador);
        t.setArquivo(new ArquivoInfo(
                nomeSeguro(arquivo.getOriginalFilename()),
                chave,
                "application/pdf",
                arquivo.getSize(),
                hash
        ));
        t.setEnviadoPor(usuario);
        t.setStatus(ehModerador(usuario) ? StatusTrabalho.PUBLICADO : StatusTrabalho.PENDENTE);

        try {
            return TrabalhoResponse.from(trabalhoRepository.saveAndFlush(t));
        } catch (DataIntegrityViolationException e) {
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            if (causa.contains("uk_trabalho_arquivo_hash")) {
                throw new RegraNegocioException("Este arquivo já foi enviado anteriormente.");
            }
            throw e;
        }
    }

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

    @Transactional(readOnly = true)
    public Page<TrabalhoResponse> listarMeus(Usuario usuario, Pageable pageable) {
        if (usuario == null) {
            throw new AccessDeniedException("Autenticação necessária para listar seus envios.");
        }
        return trabalhoRepository.findByEnviadoPorId(usuario.getId(), pageable)
                .map(TrabalhoResponse::from);
    }

    @Transactional(readOnly = true)
    public ArquivoDownload visualizar(Long id, Usuario usuario) {
        Trabalho t = obterVisivel(id, usuario);
        ArquivoInfo a = t.getArquivo();
        Resource recurso = storage.carregar(a.getChaveStorage());
        return new ArquivoDownload(recurso, a.getNomeOriginal(), a.getContentType(), a.getTamanhoBytes());
    }

    @Transactional
    public ArquivoDownload baixar(Long id, Usuario usuario, String ipOrigem) {
        Trabalho t = obterVisivel(id, usuario);
        ArquivoInfo a = t.getArquivo();

        Resource recurso = storage.carregar(a.getChaveStorage());

        trabalhoRepository.incrementarDownloads(id);
        downloadLogRepository.save(new DownloadLog(t, usuario, ipOrigem));

        return new ArquivoDownload(recurso, a.getNomeOriginal(), a.getContentType(), a.getTamanhoBytes());
    }

    @Transactional
    public TrabalhoResponse atualizar(Long id, TrabalhoRequest dados, Usuario usuario) {
        Trabalho t = obter(id);
        validarPermissaoEdicao(t, usuario);
        if (!ehModerador(usuario) && t.getStatus() != StatusTrabalho.PENDENTE) {
            throw new RegraNegocioException("Apenas trabalhos em homologação (pendentes) podem ser editados pelo autor.");
        }

        Curso novoCurso = buscarCurso(dados.cursoId());
        Orientador orientador = Objects.equals(t.getOrientador().getId(), dados.orientadorId())
                ? t.getOrientador()
                : buscarOrientador(dados.orientadorId());

        boolean mudouPasta = !Objects.equals(t.getAno(), dados.ano())
                || !Objects.equals(t.getCurso().getId(), novoCurso.getId());

        preencher(t, dados, novoCurso, orientador);

        if (mudouPasta && t.getArquivo() != null && StringUtils.hasText(t.getArquivo().getChaveStorage())) {
            String chaveAntiga = t.getArquivo().getChaveStorage();
            String novaChave = gerarChave(dados.ano(), novoCurso.getId());
            storage.mover(chaveAntiga, novaChave);
            reverterMovimentacaoEmRollback(chaveAntiga, novaChave);
            t.getArquivo().setChaveStorage(novaChave);
        }

        return TrabalhoResponse.from(trabalhoRepository.saveAndFlush(t));
    }

    @Transactional
    public TrabalhoResponse alterarStatus(Long id, StatusTrabalho novoStatus, Usuario usuario) {
        if (!ehModerador(usuario)) {
            throw new AccessDeniedException("Apenas administradores e bibliotecários podem alterar o status.");
        }
        Trabalho t = obter(id);
        t.setStatus(novoStatus);
        return TrabalhoResponse.from(trabalhoRepository.saveAndFlush(t));
    }

    @Transactional
    public void excluir(Long id, Usuario usuario) {
        Trabalho t = obter(id);
        validarPermissaoEdicao(t, usuario);
        if (!ehModerador(usuario) && t.getStatus() != StatusTrabalho.PENDENTE) {
            throw new RegraNegocioException("Apenas trabalhos em homologação (pendentes) podem ser excluídos pelo autor.");
        }
        String chave = t.getArquivo().getChaveStorage();
        trabalhoRepository.delete(t);
        limparArquivoQuando(chave, true);
    }

    private Trabalho obter(Long id) {
        return trabalhoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Trabalho", id));
    }

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
        t.setResumo(StringUtils.hasText(d.resumo()) ? d.resumo().trim() : null);
        t.setPalavrasChave(StringUtils.hasText(d.palavrasChave()) ? d.palavrasChave().trim() : null);
        t.setTipo(d.tipo());
        t.setAno(d.ano());
        t.setCurso(curso);
        t.setOrientador(orientador);
        t.getAutores().clear();
        if (d.autores() != null) {
            d.autores().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(a -> t.getAutores().add(a));
        }
        if (t.getAutores().isEmpty()) {
            throw new RegraNegocioException("Informe pelo menos um autor.");
        }
    }

    private String gerarChave(Integer ano, Long cursoId) {
        return "trabalhos/%d/%d/%s.pdf".formatted(ano, cursoId, UUID.randomUUID());
    }

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

    private void limparArquivoQuando(String chave, boolean quandoCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (quandoCommit) {
                try {
                    storage.remover(chave);
                } catch (RuntimeException e) {
                    log.warn("Não foi possível remover o arquivo {} do storage", chave, e);
                }
            }
            return;
        }
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

    private void reverterMovimentacaoEmRollback(String chaveOriginal, String chaveMovida) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        storage.mover(chaveMovida, chaveOriginal);
                    } catch (RuntimeException e) {
                        log.warn("Não foi possível reverter a movimentação do arquivo {} para {}", chaveMovida, chaveOriginal, e);
                    }
                }
            }
        });
    }
}
