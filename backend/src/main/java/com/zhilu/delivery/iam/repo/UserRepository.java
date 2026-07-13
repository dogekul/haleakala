package com.zhilu.delivery.iam.repo;

import com.zhilu.delivery.iam.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsernameAndStatus(String username, String status);
}

