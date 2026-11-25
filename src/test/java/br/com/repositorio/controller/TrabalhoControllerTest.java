package br.com.repositorio.controller;

import br.com.repositorio.dto.AlterarStatusRequest;
import br.com.repositorio.dto.ArquivoDownload;
import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.dto.TrabalhoRequest;
import br.com.repositorio.dto.TrabalhoResponse;
import br.com.repositorio.exception.RecursoNaoEncontradoException;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;
import br.com.repositorio.repository.UsuarioRepository;
import br.com.repositorio.service.TrabalhoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TrabalhoController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class TrabalhoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrabalhoService trabalhoService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private TrabalhoResponse criarMockResponse(Long id, StatusTrabalho status) {
        return new TrabalhoResponse(
                id,
                "Inteligência Artificial no Diagnóstico Médico",
                "Resumo do trabalho sobre IA e saúde.",
                "IA, Medicina, Redes Neurais",
                TipoTrabalho.TCC,
                2024,
                status,
                1L,
                "Ciência da Computação",
                1L,
                "Dr. Roberto Silva",
                10L,
                Set.of("Carlos Oliveira"),
                "artigo.pdf",
                "abcdef1234567890",
                12L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /trabalhos - deve criar trabalho com sucesso")
    void deveEnviarTrabalhoComSucesso() throws Exception {
        TrabalhoRequest request = new TrabalhoRequest(
                "Inteligência Artificial no Diagnóstico Médico",
                "Resumo do trabalho sobre IA e saúde.",
                "IA, Medicina",
                TipoTrabalho.TCC,
                2024,
                1L,
                1L,
                Set.of("Carlos Oliveira")
        );

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "artigo.pdf", "application/pdf", "%PDF-1.4 conteudo".getBytes()
        );
        MockMultipartFile dados = new MockMultipartFile(
                "dados", "", "application/json", objectMapper.writeValueAsBytes(request)
        );

        Mockito.when(trabalhoService.enviar(any(TrabalhoRequest.class), any(), any()))
                .thenReturn(criarMockResponse(1L, StatusTrabalho.PENDENTE));

        mockMvc.perform(multipart("/trabalhos").file(arquivo).file(dados))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/trabalhos/1"))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Inteligência Artificial no Diagnóstico Médico"));
    }

    @Test
    @DisplayName("GET /trabalhos - deve listar trabalhos com paginação")
    void deveBuscarTrabalhos() throws Exception {
        TrabalhoResponse item = criarMockResponse(1L, StatusTrabalho.PUBLICADO);
        Mockito.when(trabalhoService.buscar(any(TrabalhoFiltro.class), any(Pageable.class), any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/trabalhos").param("termo", "IA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /trabalhos/{id} - deve retornar trabalho por ID")
    void deveBuscarPorId() throws Exception {
        Mockito.when(trabalhoService.buscarPorId(eq(1L), any()))
                .thenReturn(criarMockResponse(1L, StatusTrabalho.PUBLICADO));

        mockMvc.perform(get("/trabalhos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("GET /trabalhos/{id} - deve retornar 404 quando não encontrado")
    void deveRetornar404QuandoNaoEncontrado() throws Exception {
        Mockito.when(trabalhoService.buscarPorId(eq(99L), any()))
                .thenThrow(new RecursoNaoEncontradoException("Trabalho", 99L));

        mockMvc.perform(get("/trabalhos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /trabalhos/anos - deve retornar lista de anos publicados")
    void deveListarAnos() throws Exception {
        Mockito.when(trabalhoService.listarAnos()).thenReturn(List.of(2024, 2023, 2022));

        mockMvc.perform(get("/trabalhos/anos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(2024))
                .andExpect(jsonPath("$[1]").value(2023));
    }

    @Test
    @DisplayName("GET /trabalhos/{id}/download - deve baixar arquivo PDF")
    void deveBaixarArquivo() throws Exception {
        byte[] conteudo = "%PDF-1.4 demo".getBytes();
        ArquivoDownload download = new ArquivoDownload(
                new ByteArrayResource(conteudo),
                "artigo.pdf",
                "application/pdf",
                (long) conteudo.length
        );

        Mockito.when(trabalhoService.baixar(eq(1L), any(), any())).thenReturn(download);

        mockMvc.perform(get("/trabalhos/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"artigo.pdf\""))
                .andExpect(content().bytes(conteudo));
    }

    @Test
    @DisplayName("PATCH /trabalhos/{id}/status - deve alterar status")
    void deveAlterarStatus() throws Exception {
        AlterarStatusRequest body = new AlterarStatusRequest(StatusTrabalho.PUBLICADO);
        Mockito.when(trabalhoService.alterarStatus(eq(1L), eq(StatusTrabalho.PUBLICADO), any()))
                .thenReturn(criarMockResponse(1L, StatusTrabalho.PUBLICADO));

        mockMvc.perform(patch("/trabalhos/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"));
    }

    @Test
    @DisplayName("DELETE /trabalhos/{id} - deve excluir trabalho")
    void deveExcluirTrabalho() throws Exception {
        mockMvc.perform(delete("/trabalhos/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(trabalhoService).excluir(eq(1L), any());
    }
}
