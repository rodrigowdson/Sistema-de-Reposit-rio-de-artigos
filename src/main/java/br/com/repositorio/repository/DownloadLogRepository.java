package br.com.repositorio.repository;

import br.com.repositorio.model.entity.DownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long> {
}
