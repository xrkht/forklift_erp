package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    boolean existsByUsername(String username);
    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("""
            select distinct u from User u
            left join u.roles r
            left join r.permissions p
            where (:includePrivileged = true
                   or not exists (
                       select privilegedRole.id
                       from u.roles privilegedRole
                       where privilegedRole.name in ('ADMIN', 'SUPER_ADMIN')
                   ))
              and (:keyword is null or :keyword = ''
                   or lower(coalesce(u.username, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.jobTag, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(r.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.code, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<User> searchPage(
            @Param("keyword") String keyword,
            @Param("includePrivileged") boolean includePrivileged,
            Pageable pageable
    );
}
