package com.claimswift.authservice.repository;

import com.claimswift.authservice.entity.LoginOtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginOtpChallengeRepository extends JpaRepository<LoginOtpChallenge, Long> {
    Optional<LoginOtpChallenge> findByChallengeId(String challengeId);

    List<LoginOtpChallenge> findByUserIdAndConsumedFalse(Long userId);
}
