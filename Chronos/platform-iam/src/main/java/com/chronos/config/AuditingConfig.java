package com.chronos.config;
 
 import java.util.Optional;
 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 import org.springframework.data.domain.AuditorAware;
 import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
 import org.springframework.security.core.Authentication;
 import org.springframework.security.core.context.SecurityContextHolder;
 
 
 @Configuration
 @EnableJpaAuditing(auditorAwareRef = "auditorAware", modifyOnCreate = false)
 public class AuditingConfig
 {
   @Bean
   public AuditorAware<String> auditorAware() {
     return () -> {
         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
         if (auth != null && auth.isAuthenticated()) {
           String name = auth.getName();
           if (name != null && !name.isEmpty())
             return Optional.of(name); 
         } 
         return Optional.of("system");
       };
   }
 }
