package mk.ukim.finki.sentimentengine.data.service;

import mk.ukim.finki.sentimentengine.data.entity.GenericEntity;
import mk.ukim.finki.sentimentengine.data.repository.GenericRepository;
import org.slf4j.Logger;

import java.util.List;

/**
 * @author kristina
 */

public abstract class GenericEntityService<E extends GenericEntity, R extends GenericRepository<E>> {

	private final R repository;

	protected GenericEntityService(R repository) {
		this.repository = repository;
	}

	protected abstract Logger getLogger();

	public E findById(Long id) {
		return repository.findById(id)
		                 .orElse(null);
	}

	public E save(E entity) {
		return repository.save(entity);
	}

	public List<E> findAll() {
		return repository.findAll();
	}

	public R getRepository() {
		return repository;
	}
}
