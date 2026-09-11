package com.chronos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;

class IamProductionBootstrapConfigTest {

    private IAdminUserRepository users;
    private IRoleRepository roles;
    private PasswordEncoder passwordEncoder;
    private IamProductionBootstrapConfig configuration;

    @BeforeEach
    void setUp() {
        users = mock(IAdminUserRepository.class);
        roles = mock(IRoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        configuration = new IamProductionBootstrapConfig();
    }

    @Test
    void existingInstallationMustNeverBeOverwritten() {
        when(users.count()).thenReturn(1L);

        configuration.bootstrapFirstAdministrator(
                users,
                roles,
                passwordEncoder,
                "replacement-admin",
                "SecurePassword1!",
                "替换管理员");

        verify(users, never()).saveAndFlush(any());
        verify(roles, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void emptyInstallationMustRequireDeploymentCredentials() {
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> configuration.bootstrapFirstAdministrator(
                users,
                roles,
                passwordEncoder,
                "",
                "",
                "系统管理员"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHRONOS_BOOTSTRAP_ADMIN_USERNAME")
                .hasMessageContaining("CHRONOS_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void emptyInstallationMustRejectWeakPassword() {
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> configuration.bootstrapFirstAdministrator(
                users,
                roles,
                passwordEncoder,
                "admin",
                "admin123",
                "系统管理员"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少 12 位");
    }

    @Test
    void emptyInstallationCreatesForcedChangeSuperAdministrator() {
        when(users.count()).thenReturn(0L);
        when(roles.findByRoleCode("SUPER_ADMIN")).thenReturn(null);
        when(roles.saveAndFlush(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("SecurePassword1!"))
                .thenReturn("bcrypt-hash");

        configuration.bootstrapFirstAdministrator(
                users,
                roles,
                passwordEncoder,
                "initial.admin",
                "SecurePassword1!",
                "首任管理员");

        ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
        verify(users).saveAndFlush(userCaptor.capture());
        AdminUser saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("initial.admin");
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(saved.getMustChangePassword()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getRoles())
                .singleElement()
                .extracting(Role::getRoleCode)
                .isEqualTo("SUPER_ADMIN");
    }
}
