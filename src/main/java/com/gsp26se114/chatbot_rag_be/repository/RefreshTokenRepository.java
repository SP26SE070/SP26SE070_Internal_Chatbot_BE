package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.RefreshToken;
import com.gsp26se114.chatbot_rag_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    // Tìm kiếm Refresh Token trong Database
    Optional<RefreshToken> findByToken(String token);

    // Xóa Token cũ khi User đăng xuất hoặc đổi mật khẩu
    @Modifying
    int deleteByUser(User user);
}