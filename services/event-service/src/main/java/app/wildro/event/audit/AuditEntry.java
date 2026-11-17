package app.wildro.event.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

/**
 * One line of the audit log. Written once, never changed.
 * <p>
 * It refers to its event by id and by slug and has no foreign key, because an
 * entry about a deletion has to outlive the row it describes.
 */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(nullable = false, updatable = false)
	private String operator;

	@Column(name = "os_user", nullable = false, updatable = false)
	private String osUser;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AuditChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AuditAction action;

	@Column(name = "entity_type", nullable = false, updatable = false)
	private String entityType;

	@Column(name = "entity_id", updatable = false)
	private Long entityId;

	@Column(name = "entity_key", nullable = false, updatable = false)
	private String entityKey;

	/**
	 * JSON, held as text on the Java side. The cast is written out because the
	 * driver sends a string and PostgreSQL will not coerce text into jsonb on its
	 * own; mapping through Hibernate's JSON support would instead depend on which
	 * Jackson generation it detects, and Boot 4 ships a different one than it
	 * looks for first.
	 */
	@Column(columnDefinition = "jsonb", updatable = false)
	@ColumnTransformer(write = "?::jsonb")
	private String details;

	protected AuditEntry() {
		// for JPA
	}

	AuditEntry(Instant occurredAt, Operator operator, AuditAction action, String entityType,
			Long entityId, String entityKey, String details) {
		this.occurredAt = occurredAt;
		this.operator = operator.name();
		this.osUser = operator.osUser();
		this.channel = operator.channel();
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.entityKey = entityKey;
		this.details = details;
	}

	public Long getId() {
		return this.id;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getOperator() {
		return this.operator;
	}

	public String getOsUser() {
		return this.osUser;
	}

	public AuditChannel getChannel() {
		return this.channel;
	}

	public AuditAction getAction() {
		return this.action;
	}

	public String getEntityType() {
		return this.entityType;
	}

	public Long getEntityId() {
		return this.entityId;
	}

	public String getEntityKey() {
		return this.entityKey;
	}

	public String getDetails() {
		return this.details;
	}

}
