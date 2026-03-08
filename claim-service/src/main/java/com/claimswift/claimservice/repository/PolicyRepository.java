package com.claimswift.claimservice.repository;

import com.claimswift.claimservice.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByPolicyholderIdOrderByExpiryDateDesc(Long policyholderId);

    Optional<Policy> findByPolicyNumberAndPolicyholderId(String policyNumber, Long policyholderId);

    boolean existsByPolicyholderId(Long policyholderId);
}
