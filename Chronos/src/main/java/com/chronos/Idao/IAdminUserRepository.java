package com.chronos.Idao;

import com.chronos.model.pojo.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("adminUserRepository")
public interface IAdminUserRepository extends JpaRepository<AdminUser, String> {
  AdminUser findByUsername(String paramString);
}


