package com.claimswift.paymentservice.repository;

import com.claimswift.paymentservice.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    List<PaymentTransaction> findByClaimId(Long claimId);

    List<PaymentTransaction> findByPolicyholderId(Long policyholderId);

    List<PaymentTransaction> findByStatus(PaymentTransaction.PaymentStatus status);

    boolean existsByClaimId(Long claimId);

    Optional<PaymentTransaction> findFirstByClaimIdAndStatus(Long claimId, PaymentTransaction.PaymentStatus status);
}
