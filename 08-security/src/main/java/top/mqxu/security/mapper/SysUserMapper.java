package top.mqxu.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.mqxu.security.entity.SysUser;

/**
 * 账号 Mapper
 *
 * @author mqxu
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
