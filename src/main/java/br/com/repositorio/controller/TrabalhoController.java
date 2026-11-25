package br.com.repositorio.controller;

import br.com.repositorio.dto.AlterarStatusRequest;
import br.com.repositorio.dto.ArquivoDownload;
import br.com.repositorio.dto.PaginaResponse;
import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.dto.TrabalhoRequest;
import br.com.repositorio.dto.TrabalhoResponse;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;
import br.com.repositorio.repository.UsuarioRepository;
import br.com.repositorio.security.UsuarioDetails;
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

@RestController
@RequestMapping("/trabalhos")
public class TrabalhoController {

    private final TrabalhoService trabalhoService;
    private final UsuarioRepository usuarioRepository;

    public TrabalhoController(TrabalhoService trabalhoService, UsuarioRepository usuarioRepository) {
        this.trabalhoService = trabalhoService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TrabalhoResponse> enviar(
            @RequestPart("dados") @Valid TrabalhoRequest dados,
            @RequestPart("arquivo") MultipartFile arquivo,
            Authentication authentication,
            UriComponentsBuilder uriBuilder
    ) {
        Usuario usuario = resolverUsuario(authentication);
        TrabalhoResponse response = trabalhoService.enviar(dados, arquivo, usuario);
        URI uri = uriBuilder.path("/trabalhos/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<TrabalhoResponse>> buscar(
            @RequestParam(required = false) Long cursoId,
            @RequestParam(required = false) Long orientadorId,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) TipoTrabalho tipo,
            @RequestParam(required = false) StatusTrabalho status,
            @RequestParam(required = false) String termo,
            @PageableDefault(size = 20, sort = "ano", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        TrabalhoFiltro filtro = new TrabalhoFiltro(cursoId, orientadorId, ano, tipo, status, termo);
        Usuario usuario = resolverUsuario(authentication);
        Page<TrabalhoResponse> pagina = trabalhoService.buscar(filtro, pageable, usuario);
        return ResponseEntity.ok(PaginaResponse.from(pagina));
    }

    @GetMapping("/anos")
    public ResponseEntity<List<Integer>> listarAnos() {
        return ResponseEntity.ok(trabalhoService.listarAnos());
    }

    @GetMapping("/meus")
    public ResponseEntity<PaginaResponse<TrabalhoResponse>> listarMeus(
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Usuario usuario = resolverUsuario(authentication);
        Page<TrabalhoResponse> pagina = trabalhoService.listarMeus(usuario, pageable);
        return ResponseEntity.ok(PaginaResponse.from(pagina));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrabalhoResponse> buscarPorId(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuario = resolverUsuario(authentication);
        return ResponseEntity.ok(trabalhoService.buscarPorId(id, usuario));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> baixar(
            @PathVariable Long id,
            @RequestParam(name = "inline", defaultValue = "false") boolean inline,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Usuario usuario = resolverUsuario(authentication);
        ArquivoDownload download = inline
                ? trabalhoService.visualizar(id, usuario)
                : trabalhoService.baixar(id, usuario, extrairIp(request));

        ContentDisposition.Builder dispositionBuilder = inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment();

        String nome = StringUtils.hasText(download.nomeOriginal()) ? download.nomeOriginal() : "trabalho.pdf";
        boolean apenasAscii = StandardCharsets.US_ASCII.newEncoder().canEncode(nome);
        ContentDisposition disposition = apenasAscii
                ? dispositionBuilder.filename(nome).build()
                : dispositionBuilder.filename(nome, StandardCharsets.UTF_8).build();

        ResponseEntity.BodyBuilder resposta = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());

        if (download.tamanhoBytes() != null) {
            resposta.contentLength(download.tamanhoBytes());
        }

        return resposta.body(download.resource());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrabalhoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid TrabalhoRequest dados,
            Authentication authentication
    ) {
        Usuario usuario = resolverUsuario(authentication);
        return ResponseEntity.ok(trabalhoService.atualizar(id, dados, usuario));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TrabalhoResponse> alterarStatus(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid AlterarStatusRequest requestBody,
            @RequestParam(name = "status", required = false) StatusTrabalho statusParam,
            Authentication authentication
    ) {
        StatusTrabalho novoStatus = (requestBody != null && requestBody.status() != null)
                ? requestBody.status()
                : statusParam;

        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status no corpo JSON ou no parâmetro 'status'.");
        }

        Usuario usuario = resolverUsuario(authentication);
        return ResponseEntity.ok(trabalhoService.alterarStatus(id, novoStatus, usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuario = resolverUsuario(authentication);
        trabalhoService.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    private Usuario resolverUsuario(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UsuarioDetails details) {
            return details.getUsuario();
        }
        if (principal instanceof Usuario usuario) {
            return usuario;
        }
        String email = authentication.getName();
        if (StringUtils.hasText(email)) {
            return usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        }
        return null;
    }

    private String extrairIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded)
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null) {
            return null;
        }
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
