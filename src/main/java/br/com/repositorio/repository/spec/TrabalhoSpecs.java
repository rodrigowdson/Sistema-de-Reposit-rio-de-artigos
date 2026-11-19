package br.com.repositorio.repository.spec;

import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.model.entity.Trabalho;
import br.com.repositorio.model.enums.StatusTrabalho;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TrabalhoSpecs {

    private TrabalhoSpecs() {
    }

    public static Specification<Trabalho> comFiltro(TrabalhoFiltro filtro, boolean apenasPublicados) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (apenasPublicados) {
                predicates.add(cb.equal(root.get("status"), StatusTrabalho.PUBLICADO));
            } else if (filtro != null && filtro.status() != null) {
                predicates.add(cb.equal(root.get("status"), filtro.status()));
            }

            if (filtro == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filtro.cursoId() != null) {
                predicates.add(cb.equal(root.get("curso").get("id"), filtro.cursoId()));
            }

            if (filtro.orientadorId() != null) {
                predicates.add(cb.equal(root.get("orientador").get("id"), filtro.orientadorId()));
            }

            if (filtro.ano() != null) {
                predicates.add(cb.equal(root.get("ano"), filtro.ano()));
            }

            if (filtro.tipo() != null) {
                predicates.add(cb.equal(root.get("tipo"), filtro.tipo()));
            }

            if (filtro.termo() != null && !filtro.termo().isBlank()) {
                String[] palavras = filtro.termo().trim().toLowerCase().split("\\s+");
                for (String palavra : palavras) {
                    String pattern = "%" + escaparLike(palavra) + "%";

                    Predicate titulo = cb.like(cb.lower(root.get("titulo")), pattern, '\\');
                    Predicate resumo = cb.like(cb.lower(root.get("resumo")), pattern, '\\');
                    Predicate palavrasChave = cb.like(cb.lower(root.get("palavrasChave")), pattern, '\\');

                    Subquery<Long> autorSub = query.subquery(Long.class);
                    Root<Trabalho> subRoot = autorSub.from(Trabalho.class);
                    Join<Trabalho, String> autorJoin = subRoot.join("autores");
                    autorSub.select(cb.literal(1L))
                            .where(
                                    cb.equal(subRoot.get("id"), root.get("id")),
                                    cb.like(cb.lower(autorJoin), pattern, '\\')
                            );
                    Predicate autor = cb.exists(autorSub);

                    predicates.add(cb.or(titulo, resumo, palavrasChave, autor));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escaparLike(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
