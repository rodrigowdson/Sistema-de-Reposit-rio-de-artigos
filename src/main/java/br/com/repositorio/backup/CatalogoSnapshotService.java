package br.com.repositorio.backup;

import br.com.repositorio.model.entity.ArquivoInfo;
import br.com.repositorio.model.entity.Curso;
import br.com.repositorio.model.entity.Orientador;
import br.com.repositorio.model.entity.Trabalho;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.Perfil;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;
import br.com.repositorio.repository.CursoRepository;
import br.com.repositorio.repository.OrientadorRepository;
import br.com.repositorio.repository.TrabalhoRepository;
import br.com.repositorio.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogoSnapshotService {

    public static final String NOME_ARQUIVO_SNAPSHOT = "catalogo-metadata.json";

    private final CursoRepository cursoRepository;
    private final OrientadorRepository orientadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final TrabalhoRepository trabalhoRepository;
    private final ObjectMapper objectMapper;

    public CatalogoSnapshotService(
            CursoRepository cursoRepository,
            OrientadorRepository orientadorRepository,
            UsuarioRepository usuarioRepository,
            TrabalhoRepository trabalhoRepository,
            ObjectMapper objectMapper
    ) {
        this.cursoRepository = cursoRepository;
        this.orientadorRepository = orientadorRepository;
        this.usuarioRepository = usuarioRepository;
        this.trabalhoRepository = trabalhoRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void exportarPara(Path arquivoDestino) throws IOException {
        List<CursoSnapshot> cursos = cursoRepository.findAllByOrderByNomeAsc().stream()
                .map(c -> new CursoSnapshot(c.getNome(), c.getSigla()))
                .toList();

        List<OrientadorSnapshot> orientadores = orientadorRepository.findAllByOrderByNomeAsc().stream()
                .map(o -> new OrientadorSnapshot(o.getNome(), o.getEmail(), o.getTitulacao(), o.isAtivo()))
                .toList();

        List<UsuarioSnapshot> usuarios = usuarioRepository.findAllByOrderByNomeAsc().stream()
                .map(u -> new UsuarioSnapshot(
                        u.getNome(),
                        u.getEmail(),
                        u.getSenhaHash(),
                        u.getPerfil(),
                        u.isAtivo(),
                        u.getCriadoEm()
                ))
                .toList();

        List<TrabalhoSnapshot> trabalhos = trabalhoRepository.findAllComRelacionamentos().stream()
                .filter(t -> t.getCurso() != null && t.getOrientador() != null && t.getArquivo() != null)
                .map(t -> new TrabalhoSnapshot(
                        t.getTitulo(),
                        t.getResumo(),
                        t.getPalavrasChave(),
                        t.getTipo(),
                        t.getAno(),
                        t.getStatus(),
                        t.getCurso().getNome(),
                        t.getOrientador().getEmail(),
                        t.getEnviadoPor() != null ? t.getEnviadoPor().getEmail() : null,
                        new LinkedHashSet<>(t.getAutores()),
                        t.getArquivo().getNomeOriginal(),
                        t.getArquivo().getChaveStorage(),
                        t.getArquivo().getContentType(),
                        t.getArquivo().getTamanhoBytes(),
                        t.getArquivo().getHashSha256(),
                        t.getTotalDownloads(),
                        t.getCriadoEm(),
                        t.getAtualizadoEm()
                ))
                .toList();

        CatalogoSnapshot snapshot = new CatalogoSnapshot(
                LocalDateTime.now(),
                cursos,
                orientadores,
                usuarios,
                trabalhos
        );

        Path pai = arquivoDestino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        Path temporario = arquivoDestino.resolveSibling(arquivoDestino.getFileName() + ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporario.toFile(), snapshot);
        Files.move(temporario, arquivoDestino, StandardCopyOption.REPLACE_EXISTING);
    }

    @Transactional
    public ResultadoImportacao importarDe(Path arquivoSnapshot, Path diretorioLocal) throws IOException {
        if (!Files.isRegularFile(arquivoSnapshot)) {
            return new ResultadoImportacao(0, 0, 0, 0);
        }

        CatalogoSnapshot snapshot = objectMapper.readValue(arquivoSnapshot.toFile(), CatalogoSnapshot.class);

        Map<String, Curso> cursosPorNome = new HashMap<>();
        for (Curso c : cursoRepository.findAll()) {
            cursosPorNome.put(normalizarChave(c.getNome()), c);
        }

        int cursosImportados = 0;
        if (snapshot.cursos() != null) {
            for (CursoSnapshot item : snapshot.cursos()) {
                if (!StringUtils.hasText(item.nome())) continue;
                String chave = normalizarChave(item.nome());
                if (!cursosPorNome.containsKey(chave)) {
                    Curso novo = new Curso();
                    novo.setNome(item.nome().trim());
                    novo.setSigla(StringUtils.hasText(item.sigla()) ? item.sigla().trim() : null);
                    cursosPorNome.put(chave, cursoRepository.save(novo));
                    cursosImportados++;
                }
            }
        }

        Map<String, Orientador> orientadoresPorEmail = new HashMap<>();
        for (Orientador o : orientadorRepository.findAll()) {
            orientadoresPorEmail.put(normalizarChave(o.getEmail()), o);
        }

        int orientadoresImportados = 0;
        if (snapshot.orientadores() != null) {
            for (OrientadorSnapshot item : snapshot.orientadores()) {
                if (!StringUtils.hasText(item.email()) || !StringUtils.hasText(item.nome())) continue;
                String chave = normalizarChave(item.email());
                if (!orientadoresPorEmail.containsKey(chave)) {
                    Orientador novo = new Orientador();
                    novo.setNome(item.nome().trim());
                    novo.setEmail(chave);
                    novo.setTitulacao(StringUtils.hasText(item.titulacao()) ? item.titulacao().trim() : null);
                    novo.setAtivo(item.ativo());
                    orientadoresPorEmail.put(chave, orientadorRepository.save(novo));
                    orientadoresImportados++;
                }
            }
        }

        Map<String, Usuario> usuariosPorEmail = new HashMap<>();
        for (Usuario u : usuarioRepository.findAll()) {
            usuariosPorEmail.put(normalizarChave(u.getEmail()), u);
        }

        int usuariosImportados = 0;
        if (snapshot.usuarios() != null) {
            for (UsuarioSnapshot item : snapshot.usuarios()) {
                if (!StringUtils.hasText(item.email()) || !StringUtils.hasText(item.nome())
                        || !StringUtils.hasText(item.senhaHash()) || item.perfil() == null) {
                    continue;
                }
                String chave = normalizarChave(item.email());
                if (!usuariosPorEmail.containsKey(chave)) {
                    Usuario novo = new Usuario();
                    novo.setNome(item.nome().trim());
                    novo.setEmail(chave);
                    novo.setSenhaHash(item.senhaHash());
                    novo.setPerfil(item.perfil());
                    novo.setAtivo(item.ativo());
                    if (item.criadoEm() != null) {
                        novo.setCriadoEm(item.criadoEm());
                    }
                    usuariosPorEmail.put(chave, usuarioRepository.save(novo));
                    usuariosImportados++;
                }
            }
        }

        Path baseNormalizada = diretorioLocal.toAbsolutePath().normalize();
        int trabalhosImportados = 0;
        if (snapshot.trabalhos() != null) {
            for (TrabalhoSnapshot item : snapshot.trabalhos()) {
                if (!StringUtils.hasText(item.arquivoHashSha256())
                        || !StringUtils.hasText(item.arquivoChave())
                        || trabalhoRepository.existsByArquivoHashSha256(item.arquivoHashSha256())) {
                    continue;
                }

                Path arquivoPdf = baseNormalizada.resolve(item.arquivoChave()).normalize();
                if (!arquivoPdf.startsWith(baseNormalizada) || !Files.isRegularFile(arquivoPdf)) {
                    continue;
                }

                Curso curso = cursosPorNome.get(normalizarChave(item.cursoNome()));
                Orientador orientador = orientadoresPorEmail.get(normalizarChave(item.orientadorEmail()));
                if (curso == null || orientador == null) {
                    continue;
                }

                Trabalho t = new Trabalho();
                t.setTitulo(item.titulo());
                t.setResumo(item.resumo());
                t.setPalavrasChave(item.palavrasChave());
                t.setTipo(item.tipo());
                t.setAno(item.ano());
                t.setStatus(item.status() != null ? item.status() : StatusTrabalho.PENDENTE);
                t.setCurso(curso);
                t.setOrientador(orientador);
                if (StringUtils.hasText(item.enviadoPorEmail())) {
                    t.setEnviadoPor(usuariosPorEmail.get(normalizarChave(item.enviadoPorEmail())));
                }
                if (item.autores() != null) {
                    t.getAutores().addAll(item.autores());
                }
                t.setArquivo(new ArquivoInfo(
                        item.arquivoNomeOriginal(),
                        item.arquivoChave(),
                        StringUtils.hasText(item.arquivoContentType()) ? item.arquivoContentType() : "application/pdf",
                        item.arquivoTamanhoBytes() != null ? item.arquivoTamanhoBytes() : Files.size(arquivoPdf),
                        item.arquivoHashSha256()
                ));
                t.setTotalDownloads(Math.max(0L, item.totalDownloads()));
                if (item.criadoEm() != null) {
                    t.setCriadoEm(item.criadoEm());
                }
                if (item.atualizadoEm() != null) {
                    t.setAtualizadoEm(item.atualizadoEm());
                }

                trabalhoRepository.save(t);
                trabalhosImportados++;
            }
        }

        return new ResultadoImportacao(cursosImportados, orientadoresImportados, usuariosImportados, trabalhosImportados);
    }

    private String normalizarChave(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }

    public record ResultadoImportacao(
            int cursos,
            int orientadores,
            int usuarios,
            int trabalhos
    ) {
        public String resumo() {
            return cursos + " curso(s), " + orientadores + " orientador(es), "
                    + usuarios + " usuário(s) e " + trabalhos + " trabalho(s)";
        }
    }

    public record CatalogoSnapshot(
            LocalDateTime geradoEm,
            List<CursoSnapshot> cursos,
            List<OrientadorSnapshot> orientadores,
            List<UsuarioSnapshot> usuarios,
            List<TrabalhoSnapshot> trabalhos
    ) {
    }

    public record CursoSnapshot(String nome, String sigla) {
    }

    public record OrientadorSnapshot(String nome, String email, String titulacao, boolean ativo) {
    }

    public record UsuarioSnapshot(
            String nome,
            String email,
            String senhaHash,
            Perfil perfil,
            boolean ativo,
            LocalDateTime criadoEm
    ) {
    }

    public record TrabalhoSnapshot(
            String titulo,
            String resumo,
            String palavrasChave,
            TipoTrabalho tipo,
            Integer ano,
            StatusTrabalho status,
            String cursoNome,
            String orientadorEmail,
            String enviadoPorEmail,
            Set<String> autores,
            String arquivoNomeOriginal,
            String arquivoChave,
            String arquivoContentType,
            Long arquivoTamanhoBytes,
            String arquivoHashSha256,
            long totalDownloads,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm
    ) {
    }
}
