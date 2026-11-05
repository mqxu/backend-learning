package top.mqxu.security.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 角色 Mapper。角色是配置数据，没有增删需求，不继承 BaseMapper
 **/
public interface RoleMapper {

    /**
     * 角色编码转 id，新增用户建关联时用
     */
    @Select("SELECT id FROM sec_role WHERE code = #{code}")
    Long selectIdByCode(@Param("code") String code);

    @Select("SELECT code FROM sec_role ORDER BY id")
    List<String> selectAllCodes();
}
