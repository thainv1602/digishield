package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link GroupMember}.
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    /** User ids that belong to a group. */
    @Query("select m.userId from GroupMember m where m.groupId = ?1")
    List<UUID> findUserIdsByGroupId(UUID groupId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupId(UUID groupId);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}
