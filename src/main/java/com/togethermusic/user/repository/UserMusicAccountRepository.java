package com.togethermusic.user.repository;

import com.togethermusic.user.entity.UserMusicAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMusicAccountRepository extends JpaRepository<UserMusicAccount, Long> {

    /**
     * 根据用户ID和平台来源查询账号绑定
     */
    Optional<UserMusicAccount> findByUserIdAndSource(Long userId, String source);

    /**
     * 查询用户的所有音乐平台账号绑定
     */
    List<UserMusicAccount> findByUserId(Long userId);

    /**
     * 查询用户在指定平台的所有绑定（理论上只有一个）
     */
    List<UserMusicAccount> findByUserIdAndSourceOrderByCreatedAtDesc(Long userId, String source);

    /**
     * 检查用户是否已绑定指定平台
     */
    boolean existsByUserIdAndSource(Long userId, String source);

    /**
     * 删除用户在指定平台的绑定
     */
    void deleteByUserIdAndSource(Long userId, String source);

    /**
     * 查询所有使用指定平台的用户绑定
     */
    List<UserMusicAccount> findBySource(String source);
}
