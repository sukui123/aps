package com.aps.infra.mapper;

import com.aps.domain.model.TokenBlacklist;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/** JWT 登出黑名单 Mapper（DB 为唯一真相源，无 Redis）。 */
@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {

    @Insert("INSERT INTO token_blacklist (jti, expires_at, created_at) "
            + "VALUES (#{jti}, #{expiresAt}, now()) ON CONFLICT (jti) DO NOTHING")
    int addIgnore(@Param("jti") String jti, @Param("expiresAt") Instant expiresAt);

    @Select("SELECT count(*) FROM token_blacklist WHERE jti = #{jti} AND expires_at > now()")
    int countActive(@Param("jti") String jti);
}
