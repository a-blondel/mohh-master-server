package com.ea.repositories;

import com.ea.entities.BlacklistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistRepository extends JpaRepository<BlacklistEntity, Long> {

    boolean existsByIp(String ip);

}
