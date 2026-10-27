package top.mqxu.security.repository;

import org.springframework.stereotype.Repository;
import top.mqxu.security.model.Task;
import top.mqxu.security.model.TaskStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务仓库的内存实现。换数据库时只改这个类，上层 TaskService 不用动
 **/
@Repository
public class TaskRepository {

    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong();

    /**
     * 预置两条数据，教师登录后列表不是空的，演示批改流程时不用先提交
     */
    public TaskRepository() {
        save(new Task("实训一：用户登录接口",
                "登录接口做完了，密码用 BCrypt 加密，返回 JWT。",
                "student01", "朱彦清", Instant.now().minus(2, ChronoUnit.HOURS)));

        Task reviewed = new Task("实训二：接口鉴权",
                "教师能批改，学生只能看自己的。",
                "student02", "李思远", Instant.now().minus(1, ChronoUnit.DAYS));
        reviewed.reviewed(92, "归属判断写对了。令牌过期要返回 401，别返回 200。",
                Instant.now().minus(20, ChronoUnit.HOURS));
        save(reviewed);
    }

    public Task save(Task task) {
        if (task.getId() == null) {
            task.setId(ids.incrementAndGet());
        }
        tasks.put(task.getId(), task);
        return task;
    }

    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    /**
     * 按提交人过滤。用户名来自令牌，不是来自请求参数
     */
    public List<Task> findByOwner(String username) {
        return tasks.values().stream()
                .filter(task -> task.getOwnerUsername().equals(username))
                .sorted(Comparator.comparing(Task::getSubmittedAt).reversed())
                .toList();
    }

    public List<Task> findAll() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(Task::getSubmittedAt).reversed())
                .toList();
    }

    public boolean existsUnreviewed(String username, String title) {
        return tasks.values().stream()
                .anyMatch(task -> task.getOwnerUsername().equals(username)
                        && task.getTitle().equals(title)
                        && task.getStatus() == TaskStatus.SUBMITTED);
    }
}
