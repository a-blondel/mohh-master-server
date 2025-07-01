package com.ea.repositories;

import com.ea.entities.PersonaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaRepository extends JpaRepository<PersonaEntity, Long> {

    @Query("SELECT p FROM PersonaEntity p WHERE LOWER(p.pers) = LOWER(:pers)")
    Optional<PersonaEntity> findByPers(@Param("pers") String pers);

    @Query(value = "SELECT * FROM PERSONA p WHERE LOWER(p.pers) LIKE LOWER(CONCAT('%', :searchTerm, '%')) LIMIT :maxResults", nativeQuery = true)
    List<PersonaEntity> findByPersLike(@Param("searchTerm") String searchTerm, @Param("maxResults") int maxResults);

}
