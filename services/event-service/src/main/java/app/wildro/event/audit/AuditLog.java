package app.wildro.event.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Storage for audit entries. Append-only in practice: nothing here updates or deletes. */
public interface AuditLog extends JpaRepository<AuditEntry, Long> {

	List<AuditEntry> findByEntityTypeAndEntityKeyOrderByOccurredAtAscIdAsc(String entityType, String entityKey);

}
