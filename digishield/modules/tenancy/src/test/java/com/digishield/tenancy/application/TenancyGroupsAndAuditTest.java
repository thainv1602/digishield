package com.digishield.tenancy.application;

import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.tenancy.api.GroupView;
import com.digishield.tenancy.domain.AuditLog;
import com.digishield.tenancy.domain.Group;
import com.digishield.tenancy.domain.GroupMember;
import com.digishield.tenancy.infrastructure.AuditLogRepository;
import com.digishield.tenancy.infrastructure.GroupMemberRepository;
import com.digishield.tenancy.infrastructure.GroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Groups and the audit trail — the two parts of tenancy nothing exercised.
 *
 * <p>Both carry rules that only exist in this service: a group belongs to one
 * tenant and a lookup from another must not find it, a member is not added
 * twice, and entering your own tenant is not impersonation.
 */
@ExtendWith(MockitoExtension.class)
class TenancyGroupsAndAuditTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private org.springframework.beans.factory.ObjectProvider<
            com.digishield.tenancy.api.TenantAdminProvisioner> adminProvisioner;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TenancyServiceImpl tenancyService;

    @Captor
    private ArgumentCaptor<AuditLog> auditCaptor;

    @Captor
    private ArgumentCaptor<Group> groupCaptor;

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT.toString());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Group staticGroup(UUID id, UUID tenantId, String name) {
        return new Group(id, tenantId, name, null, 0);
    }

    // ---- groups ------------------------------------------------------------

    @Test
    @DisplayName("a static group is created empty; only a smart group materialises members")
    void createStaticGroupStartsWithNoMembers() {
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupView created = tenancyService.createGroup(TENANT, new GroupView(null, "Kế toán", null, null));

        assertThat(created.name()).isEqualTo("Kế toán");
        assertThat(created.memberCount()).isZero();
        // No rule means nothing to evaluate: the group is filled by adding people.
        verify(groupMemberRepository, never()).findUserIdsByGroupId(any());
    }

    @Test
    @DisplayName("a group of another tenant is not found, rather than returned")
    void groupOfAnotherTenantIsNotVisible() {
        UUID groupId = UUID.randomUUID();
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(staticGroup(groupId, OTHER_TENANT, "Foreign")));

        // BR-7: the row exists, but not for this caller. Treated as absent so a
        // probe cannot tell "not yours" from "not there".
        assertThatThrownBy(() -> tenancyService.listGroupMembers(TENANT, groupId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(groupId.toString());
    }

    @Test
    void deletingAGroupOfAnotherTenantIsRefusedAndDeletesNothing() {
        UUID groupId = UUID.randomUUID();
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(staticGroup(groupId, OTHER_TENANT, "Foreign")));

        assertThatThrownBy(() -> tenancyService.deleteGroup(TENANT, groupId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(groupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("adding the same member twice writes one row and reports one member")
    void addingAMemberIsIdempotent() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(staticGroup(groupId, TENANT, "Kế toán")));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(1);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(1L);

        var count = tenancyService.addGroupMember(TENANT, groupId, userId);

        assertThat(count.memberCount()).isEqualTo(1);
        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    @DisplayName("a user outside the tenant cannot be added to its group")
    void addingAUserFromAnotherTenantIsRefused() {
        UUID groupId = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(staticGroup(groupId, TENANT, "Kế toán")));
        // The membership check counts users in *this* tenant; an outsider is 0.
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> tenancyService.addGroupMember(TENANT, groupId, outsider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(outsider.toString());

        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    void removingAMemberRecountsTheGroup() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(staticGroup(groupId, TENANT, "Kế toán")));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.countByGroupId(groupId)).thenReturn(2L);

        var count = tenancyService.removeGroupMember(TENANT, groupId, userId);

        verify(groupMemberRepository).deleteByGroupIdAndUserId(groupId, userId);
        assertThat(count.memberCount()).isEqualTo(2);
        // The stored count is refreshed too, not just the returned one.
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(2);
    }

    @Test
    void renamingAGroupTrimsAndKeepsTheRuleUntouched() {
        UUID groupId = UUID.randomUUID();
        Group existing = new Group(groupId, TENANT, "Cũ", "{\"department\":\"ke-toan\"}", 3);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existing));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupView updated = tenancyService.updateGroup(
                TENANT, groupId, new GroupView(null, "  Kế toán  ", null, null));

        assertThat(updated.name()).isEqualTo("Kế toán");
        assertThat(existing.getRuleJson()).isEqualTo("{\"department\":\"ke-toan\"}");
    }

    @Test
    @DisplayName("an empty rule turns a smart group into a static one")
    void anEmptyRuleClearsIt() {
        UUID groupId = UUID.randomUUID();
        Group existing = new Group(groupId, TENANT, "Smart", "{\"risk_score_gte\":70}", 5);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existing));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        tenancyService.updateGroup(TENANT, groupId, new GroupView(null, null, Map.of(), null));

        assertThat(existing.getRuleJson()).isNull();
    }

    @Test
    void listingGroupsReturnsThemInTheRepositoryOrder() {
        when(groupRepository.findByTenantIdOrderByName(TENANT)).thenReturn(List.of(
                staticGroup(UUID.randomUUID(), TENANT, "An toàn"),
                staticGroup(UUID.randomUUID(), TENANT, "Kế toán")));

        assertThat(tenancyService.listGroups(TENANT))
                .extracting(GroupView::name)
                .containsExactly("An toàn", "Kế toán");
    }

    // ---- audit -------------------------------------------------------------

    @Test
    @DisplayName("an audit entry defaults a missing actor and severity rather than storing null")
    void auditFillsInWhatTheCallerOmitted() {
        tenancyService.recordAudit(TENANT, "  ", "user.delete", "user:42", "10.0.0.1", null);

        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog entry = auditCaptor.getValue();
        assertThat(entry.getActor()).isEqualTo("unknown");
        assertThat(entry.getSeverity()).isEqualTo("standard");
        assertThat(entry.getAction()).isEqualTo("user.delete");
        assertThat(entry.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("entering your own tenant is not impersonation and is not recorded")
    void enteringYourOwnTenantIsNotRecorded() {
        tenancyService.recordImpersonation(TENANT, "super@digishield.vn", "10.0.0.1");

        // Recording it as `critical` buried the entries that matter.
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("entering another tenant is recorded in that tenant, then the operator's context is restored")
    void enteringAnotherTenantIsRecordedThere() {
        tenancyService.recordImpersonation(OTHER_TENANT, "super@digishield.vn", "10.0.0.1");

        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog entry = auditCaptor.getValue();
        // BR-6: the trace belongs to the tenant being entered, so its own admins
        // can see who came in.
        assertThat(entry.getTenantId()).isEqualTo(OTHER_TENANT);
        assertThat(entry.getAction()).isEqualTo("tenant.impersonate.start");
        assertThat(entry.getSeverity()).isEqualTo("critical");
        // The operator is put back where they were, not left inside the tenant.
        assertThat(TenantContext.get()).isEqualTo(TENANT.toString());
    }
}
