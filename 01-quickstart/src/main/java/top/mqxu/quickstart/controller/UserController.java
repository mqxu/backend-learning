package top.mqxu.quickstart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.quickstart.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author mqxu
 * @date 2026/9/9
 * @description UserController
 **/
@RestController
@RequestMapping("/user")
public class UserController {

    /**
     * 用一个线程安全的内存列表充当数据源。
     * 入门阶段先不引入数据库，把注意力放在请求怎么进来、对象怎么出去。
     */
    private final List<User> users = new CopyOnWriteArrayList<>(List.of(
            new User(1001L, "张三", LocalDate.of(2005, 10, 24)),
            new User(1002L, "李四", LocalDate.of(2004, 3, 8)),
            new User(1003L, "王五", LocalDate.of(2006, 11, 30))
    ));

    private final AtomicLong idGenerator = new AtomicLong(1003L);

    /**
     * 返回纯文本。String 返回值不经过 Jackson，直接写进响应体。
     */
    @GetMapping("/hello")
    public String getHello() {
        return "hello world";
    }

    /**
     * 返回对象。Spring MVC 用 Jackson 把它序列化成 JSON。
     */
    @GetMapping("/info")
    public User getUserInfo() {
        return new User(1001L, "张三", LocalDate.of(2005, 10, 24));
    }

    /**
     * 查询用户列表。keyword 可选，缺省时返回全部。
     */
    @GetMapping("/list")
    public List<User> list(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return users;
        }
        return users.stream().filter(u -> u.getName().contains(keyword)).toList();
    }

    /**
     * 路径参数。占位符名与方法参数名一致时，@PathVariable 可以不写值。
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return users.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 接收 JSON 请求体。@RequestBody 负责把请求体反序列化成 User。
     */
    @PostMapping
    public User create(@RequestBody User user) {
        user.setId(idGenerator.incrementAndGet());
        users.add(user);
        return user;
    }
}
