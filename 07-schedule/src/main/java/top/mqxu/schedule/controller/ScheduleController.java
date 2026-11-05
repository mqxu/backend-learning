package top.mqxu.schedule.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.schedule.service.DynamicTaskService;
import top.mqxu.schedule.service.TaskExecutionRecorder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务查询与管理接口
 *
 * @author mqxu
 * @date 2026/9/16
 * @description ScheduleController
 **/
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final TaskExecutionRecorder recorder;
    private final DynamicTaskService dynamicTaskService;

    @GetMapping("/hello")
    public String hello() {
        return "定时任务 模块";
    }

    /**
     * 各任务的执行次数、最近执行时间、平均耗时
     */
    @GetMapping("/tasks")
    public Map<String, Object> tasks() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("统计", recorder.stats());
        result.put("说明", "fixedRate 每 3 秒、fixedDelay 每 5 秒、cron 每 10 秒各跑一次");
        return result;
    }

    /**
     * 最近的执行记录，默认 20 条
     */
    @GetMapping("/tasks/records")
    public List<TaskExecutionRecorder.Execution> records(@RequestParam(defaultValue = "20") int limit) {
        return recorder.recent(limit);
    }

    /**
     * 运行期注册一个动态任务
     */
    @PostMapping("/tasks/dynamic")
    public Map<String, Object> registerDynamic(@RequestParam String name,
                                               @RequestParam(defaultValue = "0/5 * * * * ?") String cron) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("success", true);
            result.put("运行中", dynamicTaskService.register(name, cron));
            result.put("cron", cron);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 取消动态任务
     */
    @DeleteMapping("/tasks/dynamic/{name}")
    public Map<String, Object> cancelDynamic(@PathVariable String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean cancelled = dynamicTaskService.cancel(name);
        result.put("success", cancelled);
        result.put("message", cancelled ? "已取消" : "任务不存在");
        return result;
    }

    /**
     * 当前在跑的动态任务
     */
    @GetMapping("/tasks/dynamic")
    public Map<String, Object> dynamicTasks() {
        return dynamicTaskService.list();
    }
}
