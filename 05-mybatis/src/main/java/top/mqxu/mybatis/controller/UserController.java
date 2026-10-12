package top.mqxu.mybatis.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.mybatis.dto.UserQuery;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户接口，单表增删改查与各类条件查询
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /* ==================== 增 ==================== */

    /** 新增用户 */
    @PostMapping
    public User create(@RequestBody User user) {
        userService.createUser(user);
        return user;
    }

    /** 批量新增，返回成功条数 */
    @PostMapping("/batch")
    public int createBatch(@RequestBody List<User> users) {
        userService.createUserBatch(users);
        return users.size();
    }

    /* ==================== 查 ==================== */

    /** 按主键查询 */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getUser(id);
    }

    /** 条件列表，不传参数即查全部 */
    @GetMapping
    public List<User> list(UserQuery query) {
        return userService.listByCondition(query);
    }

    /** 条件分页 */
    @GetMapping("/page")
    public Page<User> page(UserQuery query,
                           @RequestParam(defaultValue = "1") long current,
                           @RequestParam(defaultValue = "10") long size) {
        return userService.pageByCondition(query, current, size);
    }

    /** 关键词模糊匹配 username 或 nickname */
    @GetMapping("/keyword")
    public List<User> keyword(@RequestParam String keyword) {
        return userService.searchByKeyword(keyword);
    }

    /** 年龄区间查询 */
    @GetMapping("/age-range")
    public List<User> ageRange(@RequestParam(required = false) Integer minAge,
                               @RequestParam(required = false) Integer maxAge) {
        return userService.listByAgeRange(minAge, maxAge);
    }

    /** 按积分倒序取前 N 条 */
    @GetMapping("/top")
    public List<User> top(@RequestParam(defaultValue = "3") Integer limit) {
        return userService.listTop(limit);
    }

    /** 只取用户名列 */
    @GetMapping("/usernames")
    public List<String> usernames() {
        return userService.listUsernames();
    }

    /** 按 JSON 标签查询 */
    @GetMapping("/tag")
    public List<User> byTag(@RequestParam String tag) {
        return userService.listByTag(tag);
    }

    /** 用户名是否已存在 */
    @GetMapping("/exists")
    public boolean exists(@RequestParam String username) {
        return userService.existsUsername(username);
    }

    /** 按性别统计人数 */
    @GetMapping("/count")
    public long count(@RequestParam(required = false) Integer gender) {
        return userService.countByGender(gender);
    }

    /** 汇总统计：总数、余额合计、最大年龄、按性别分组人数 */
    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        return userService.statistics();
    }

    /* ==================== 改 ==================== */

    /** 按主键更新，路径上的 id 覆盖请求体中的 id */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.modifyUser(user);
        return userService.getUser(id);
    }

    /** 只改状态 */
    @PutMapping("/{id}/status")
    public boolean updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.modifyUserStatus(id, status);
    }

    /** 充值 */
    @PostMapping("/{id}/recharge")
    public User recharge(@PathVariable Long id, @RequestParam BigDecimal amount) {
        userService.recharge(id, amount);
        return userService.getUser(id);
    }

    /** 按用户名改邮箱，路径是字面量，会比 /{id} 优先匹配 */
    @PutMapping("/email")
    public boolean updateEmail(@RequestParam String username, @RequestParam String email) {
        return userService.modifyEmailByUsername(username, email);
    }

    /* ==================== 删 ==================== */

    /** 按主键删除（逻辑删除） */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return userService.removeUser(id);
    }

    /** 批量删除，ids 用逗号分隔，如 1,2,3 */
    @DeleteMapping("/batch")
    public boolean deleteBatch(@RequestParam List<Long> ids) {
        return userService.removeUserBatch(ids);
    }

    /** 按状态批量删除 */
    @DeleteMapping("/status/{status}")
    public boolean deleteByStatus(@PathVariable Integer status) {
        return userService.removeUserByStatus(status);
    }
}
