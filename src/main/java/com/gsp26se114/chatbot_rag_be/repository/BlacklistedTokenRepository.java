package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    /**
     * Kiểm tra xem Token có nằm trong danh sách đen hay không.
     * Được dùng trong JwtAuthenticationFilter để chặn các request dùng Token đã Logout.
     */
    boolean existsByToken(String token);

    /**
     * Tìm kiếm thông tin Token bị chặn (nếu cần xử lý thêm logic thời gian).
     */
    Optional<BlacklistedToken> findByToken(String token);
}