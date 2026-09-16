package com.nexa.api.repository;
import com.nexa.api.beans.RefreshTokenEntity;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}

