package br.com.repositorio.controller;

import br.com.repositorio.dto.AlterarStatusRequest;
import br.com.repositorio.dto.ArquivoDownload;
import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.dto.TrabalhoRequest;
import br.com.repositorio.dto.TrabalhoResponse;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.repository.UsuarioRepository;
import br.com.repositorio.service.TrabalhoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/trabalhos")
public class TrabalhoController {

    private final TrabalhoService trabalhoService;
    private final UsuarioRepository usuarioRepository;

    public TrabalhoController(TrabalhoService trabalhoService, UsuarioRepository usuarioRepository) {
        this.trabalhoService = trabalhoService;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Envia um novo trabalho com seu respectivo arquivo PDF.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TrabalhoResponse> enviar(
            @RequestPart("dados") @Valid TrabalhoRequest dados,
            @RequestPart("arquivo") MultipartFile arquivo,
            Authentication authentication,
            HttpServletRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        Usuario usuario = resolverUsuario(authentication, request);
        TrabalhoResponse response = trabalhoService.enviar(dados, arquivo, usuario);
        URI uri = uriBuilder.path("/trabalhos/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    /**
     * Consulta trabalhos cadastrados com suporte a paginação e filtros dinâmicos.
     */
    @GetMapping
    public ResponseEntity<Page<TrabalhoResponse>> buscar(
            @ModelAttribute TrabalhoFiltro filtro,
            @PageableDefault(size = 20, sort = "ano", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Usuario usuario = resolverUsuario(authentication, request);
        Page<TrabalhoResponse> resultado = trabalhoService.buscar(filtro, pageable, usuario);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Busca os detalhes de um trabalho específico por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TrabalhoResponse> buscarPorId(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Usuario usuario = resolverUsuario(authentication, request);
        TrabalhoResponse response = trabalhoService.buscarPorId(id, usuario);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista todos os anos com trabalhos publicados em ordem decrescente.
     */
    @GetMapping("/anos")
    public ResponseEntity<List<Integer>> listarAnos() {
        List<Integer> anos = trabalhoService.listarAnos();
        return ResponseEntity.ok(anos);
    }

    /**
     * Realiza o download do arquivo PDF associado ao trabalho.
     * Suporta visualização inline através do parâmetro ?inline=true.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> baixar(
            @PathVariable Long id,
            @RequestParam(name = "inline", defaultValue = "false") boolean inline,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Usuario usuario = resolverUsuario(authentication, request);
        String ipOrigem = extrairIp(request);
        ArquivoDownload download = trabalhoService.baixar(id, usuario, ipOrigem);

        ContentDisposition.Builder dispositionBuilder = inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment();

        ContentDisposition disposition = dispositionBuilder
                .filename(download.nomeOriginal(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    /**
     * Atualiza os metadados de um trabalho existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TrabalhoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid TrabalhoRequest dados,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Usuario usuario = resolverUsuario(authentication, request);
        TrabalhoResponse response = trabalhoService.atualizar(id, dados, usuario);
        return ResponseEntity.ok(response);
    }

    /**
     * Altera o status de moderação do trabalho (PENDENTE, PUBLICADO, ARQUIVADO).
     * O status pode ser enviado no corpo da requisição JSON ou como parâmetro de consulta.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TrabalhoResponse> alterarStatus(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid AlterarStatusRequest requestBody,
            @RequestParam(name = "status", required = false) StatusTrabalho statusParam,
            Authentication authentication,
            HttpServletRequest request
    ) {
        StatusTrabalho novoStatus = (requestBody != null && requestBody.status() != null)
                ? requestBody.status()
                : statusParam;

        if (novoStatus == null) {
            throw new RegraNegocioException("O novo status deve ser informado no corpo da requisição ou como parâmetro 'status'.");
        }

        Usuario usuario = resolverUsuario(authentication, request);
        TrabalhoResponse response = trabalhoService.alterarStatus(id, novoStatus, usuario);
        return ResponseEntity.ok(response);
    }

    /**
     * Exclui um trabalho e seu respectivo arquivo físico.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Usuario usuario = resolverUsuario(authentication, request);
        trabalhoService.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    private String extrairIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Usuario resolverUsuario(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Usuario u) {
                return u;
            }
            String email = authentication.getName();
            if (StringUtils.hasText(email)) {
                Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
                if (usuario.isPresent()) {
                    return usuario.get();
                }
            }
        }

        if (request != null) {
            String emailHeader = request.getHeader("X-Usuario-Email");
            if (StringUtils.hasText(emailHeader)) {
                Optional<Usuario> usuario = usuarioRepository.findByEmail(emailHeader);
                if (usuario.isPresent()) {
                    return usuario.get();
                }
            }
            String idHeader = request.getHeader("X-Usuario-Id");
            if (StringUtils.hasText(idHeader)) {
                try {
                    return usuarioRepository.findById(Long.parseLong(idHeader)).orElse(null);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }
}
