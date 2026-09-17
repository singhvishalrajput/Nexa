package com.nexa.api.repository;

import com.nexa.api.beans.UserEntity;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Login identity is a customer; passwords and rotating sessions are credentials. */
@Repository
public class UserRepository {
  private final JdbcTemplate db;

  public UserRepository(JdbcTemplate db) {
    this.db = db;
  }

  private Optional<UserEntity> find(String predicate, String value) {
    return db
        .query(
            "SELECT c.*,k.password_hash FROM customers c JOIN customer_credentials k ON"
                + " k.user_id=c.user_id AND k.credential_type='PASSWORD' WHERE "
                + predicate,
            (r, n) -> {
              var u =
                  new UserEntity(
                      r.getString("user_id"),
                      r.getString("email"),
                      r.getString("password_hash"),
                      r.getString("role"),
                      r.getString("full_name"),
                      r.getString("phone_number"),
                      r.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC));
              u.setStatus(r.getString("status"));
              return u;
            },
            value)
        .stream()
        .findFirst();
  }

  public Optional<UserEntity> findById(String id) {
    return find("c.user_id=?", id);
  }

  public Optional<UserEntity> findByEmailIgnoreCase(String email) {
    return find("LOWER(c.email)=LOWER(?)", email);
  }

  public boolean existsByEmailIgnoreCase(String email) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM customers WHERE LOWER(email)=LOWER(?)", Long.class, email)
        > 0;
  }

  @Transactional
  public UserEntity saveAndFlush(UserEntity u) {
    var now = Timestamp.from(java.time.Instant.now());
    if (findById(u.getId()).isEmpty()) {
      db.update(
          "INSERT INTO"
              + " customers(user_id,email,full_name,phone_number,status,role,created_at,updated_at)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          u.getId(),
          u.getEmail(),
          u.getFullName(),
          u.getPhoneNumber(),
          u.getStatus(),
          u.getRole(),
          now,
          now);
      db.update(
          "INSERT INTO customer_credentials(id,user_id,credential_type,password_hash,created_at)"
              + " VALUES(?,?,'PASSWORD',?,?)",
          u.getId(),
          u.getId(),
          u.getPasswordHash(),
          now);
    } else
      db.update(
          "UPDATE customers SET full_name=?,phone_number=?,updated_at=? WHERE user_id=?",
          u.getFullName(),
          u.getPhoneNumber(),
          now,
          u.getId());
    return u;
  }
}
