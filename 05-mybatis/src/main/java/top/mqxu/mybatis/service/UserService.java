package top.mqxu.mybatis.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import top.mqxu.mybatis.dto.UserQuery;
import top.mqxu.mybatis.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户服务
 *
 * <p>继承 {@link IService} 后已自带 save / saveBatch / getById / listByIds / list / page / count /
 * updateById / removeById / removeByIds / getOne / exists 等通用方法，
 * 下面只补充带业务语义的封装和各类 Lambda 条件查询示例。</p>
 *
 * @author mqxu
 */
public interface UserService extends IService<User> {

    /* ==================== 增 ==================== */

    /** 新增用户，带默认值兜底、业务校验与事务 */
    boolean createUser(User user);

    /** 批量新增 */
    boolean createUserBatch(List<User> users);

    /* ==================== 删 ==================== */

    /** 按主键删除（逻辑删除，实为 update deleted = 1） */
    boolean removeUser(Long id);

    /** 按主键批量删除 */
    boolean removeUserBatch(List<Long> ids);

    /** 按状态删除，演示条件删除 */
    boolean removeUserByStatus(Integer status);

    /* ==================== 改 ==================== */

    /** 按主键更新，先确认记录存在 */
    boolean modifyUser(User user);

    /** 只更新 status 一个字段（LambdaUpdateWrapper） */
    boolean modifyUserStatus(Long id, Integer status);

    /** 充值，先查后改，靠乐观锁避免并发覆盖 */
    boolean recharge(Long id, BigDecimal amount);

    /* ==================== 查（基础） ==================== */

    /** 按主键查询，不存在返回 null */
    User getUser(Long id);

    /** 查询全部 */
    List<User> listUsers();

    /** 无条件分页 */
    Page<User> pageUsers(long current, long size);

    /* ==================== 查（Lambda 条件） ==================== */

    /** 动态条件列表，条件全空等价于查全部 */
    List<User> listByCondition(UserQuery query);

    /** 动态条件分页 */
    Page<User> pageByCondition(UserQuery query, long current, long size);

    /** 关键词模糊匹配 username 或 nickname */
    List<User> searchByKeyword(String keyword);

    /** 按年龄区间查询 */
    List<User> listByAgeRange(Integer minAge, Integer maxAge);

    /** 按积分倒序取前 N 条 */
    List<User> listTop(Integer limit);

    /** 只查 username 一列 */
    List<String> listUsernames();

    /** 按 JSON 列 tags 中的标签查询 */
    List<User> listByTag(String tag);

    /** 判断用户名是否已存在 */
    boolean existsUsername(String username);

    /** 按性别统计人数 */
    long countByGender(Integer gender);

    /** 汇总统计：总数、余额合计、最大年龄、按性别分组人数 */
    Map<String, Object> statistics();
}
