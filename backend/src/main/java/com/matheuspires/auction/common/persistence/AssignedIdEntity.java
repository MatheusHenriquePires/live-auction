package com.matheuspires.auction.common.persistence;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base class for entities whose UUID is assigned by the application (see {@code Ids}).
 * <p>
 * Implementing {@link Persistable} lets Spring Data call {@code persist} for new entities instead of
 * {@code merge}, which would otherwise issue a useless SELECT before every INSERT.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean persisted;

	protected AssignedIdEntity() {
	}

	protected AssignedIdEntity(UUID id) {
		this.id = Objects.requireNonNull(id, "id");
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return !persisted;
	}

	@PostPersist
	@PostLoad
	void markPersisted() {
		this.persisted = true;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return id != null && id.equals(((AssignedIdEntity) other).id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
