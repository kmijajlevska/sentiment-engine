package mk.ukim.finki.sentimentengine.data.repository;

import mk.ukim.finki.sentimentengine.data.entity.GenericEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * @author kristina
 */
@NoRepositoryBean
public interface GenericRepository<E extends GenericEntity> extends JpaRepository<E, Long> {
}
