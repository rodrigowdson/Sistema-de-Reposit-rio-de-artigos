package br.com.repositorio.model.entity;

import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "trabalho")
@Getter @Setter @NoArgsConstructor
public class Trabalho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String resumo;

    @Column(name = "palavras_chave", length = 500)
    private String palavrasChave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTrabalho tipo;

    @Column(nullable = false)
    private Integer ano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTrabalho status = StatusTrabalho.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orientador_id", nullable = false)
    private Orientador orientador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enviado_por_id")
    private Usuario enviadoPor;

    @ElementCollection
    @CollectionTable(name = "trabalho_autor", joinColumns = @JoinColumn(name = "trabalho_id"))
    @Column(name = "nome_autor", nullable = false, length = 150)
    @BatchSize(size = 50)
    private Set<String> autores = new LinkedHashSet<>();

    @Embedded
    private ArquivoInfo arquivo;

    @Column(name = "total_downloads", nullable = false)
    private long totalDownloads;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }
}
