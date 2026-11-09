package com.chronos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.pojo.Position;
import com.chronos.service.iService.IIamDirectoryService;
import com.chronos.service.impl.DirectoryImportService;
import com.chronos.service.impl.BusinessNumberService;
import com.chronos.security.IamAuthorization;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.Idao.IPositionRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import org.springframework.security.core.Authentication;

class IamDirectoryControllerTest {

    private IIamDirectoryService service;
    private IamDirectoryController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = mock(IIamDirectoryService.class);
        authentication = mock(Authentication.class);
        controller = new IamDirectoryController(
                service,
                mock(DirectoryImportService.class),
                mock(BusinessNumberService.class),
                mock(IamAuthorization.class),
                mock(DirectoryDataScopeGuard.class),
                mock(IPositionRepository.class),
                mock(IJobLevelRepository.class),
                mock(IEmployeeAssignmentRepository.class),
                mock(IOrganizationUnitRepository.class));
    }

    @Test
    void positionsShouldReturnStablePageView() {
        Position position = new Position();
        position.setPositionName("班主任");
        PageRequest pageable = PageRequest.of(0, 10);
        when(service.pagePositions("org-1", pageable))
                .thenReturn(new PageImpl<>(List.of(position), pageable, 12));

        ResultData<?> response = controller.positions("org-1", 0, 10, authentication);

        PageView<?> page = assertPage(response, 12);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst()).isSameAs(position);
    }

    @Test
    void jobLevelsShouldReturnStablePageView() {
        JobLevel level = new JobLevel();
        level.setLevelName("中级");
        PageRequest pageable = PageRequest.of(0, 20);
        when(service.pageJobLevels("org-1", pageable))
                .thenReturn(new PageImpl<>(List.of(level), pageable, 21));

        ResultData<?> response = controller.jobLevels("org-1", 0, 20, authentication);

        PageView<?> page = assertPage(response, 21);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst()).isSameAs(level);
    }

    @Test
    void employeesShouldReturnStablePageView() {
        Employee employee = new Employee();
        employee.setEmployeeName("测试教师");
        PageRequest pageable = PageRequest.of(1, 5);
        when(service.pageEmployees("org-1", "教师", pageable))
                .thenReturn(new PageImpl<>(
                        List.of(employee, new Employee(), new Employee()),
                        pageable,
                        8));

        ResultData<?> response = controller.employees("org-1", 1, 5, "教师", authentication);

        PageView<?> page = assertPage(response, 8);
        assertThat(page.content()).hasSize(3);
        assertThat(page.content().getFirst()).isSameAs(employee);
        assertThat(page.number()).isEqualTo(1);
    }

    private PageView<?> assertPage(ResultData<?> response, long totalElements) {
        assertThat(response.getCode()).isEqualTo("200");
        assertThat(response.getData()).isInstanceOf(PageView.class);
        PageView<?> page = (PageView<?>) response.getData();
        assertThat(page.totalElements()).isEqualTo(totalElements);
        return page;
    }
}
