package top.mqxu.security.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.mqxu.security.model.UserRoleEntry;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户角色关联 Mapper
 * <p>
 * 关联表没有自己的主键（主键是 user_id + role_id），写不进 BaseMapper 那一套，
 * 直接用注解 SQL 更直白。
 **/
public interface UserRoleMapper {

    /**
     * 取一个用户的角色编码，登录时用
     */
    @Select("""
            SELECT r.code
            FROM sec_user_role ur
            JOIN sec_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 一次取回全部关联，账号列表用，避免逐个用户查一遍
     */
    @Select("""
            SELECT ur.user_id AS user_id, r.code AS role_code
            FROM sec_user_role ur
            JOIN sec_role r ON r.id = ur.role_id
            ORDER BY ur.user_id, r.id
            """)
    List<UserRoleEntry> selectAllEntries();

    @Insert("INSERT INTO sec_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sec_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
