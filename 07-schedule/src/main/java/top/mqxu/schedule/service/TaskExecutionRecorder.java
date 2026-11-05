package top.mqxu.schedule.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 任务执行记录器：把每次执行的时间、耗时、线程名留下来，方便在接口里核对任务是否真的按周期跑了。
 * 生产上这类记录应该落到数据库或日志系统，这里用内存队列演示，只保留最近若干条。
 *
 * @author mqxu
 */
@Component
public class TaskExecutionRecorder {

    /** 最多保留多少条记录，避免内存无限增长 */
    private static final int CAPACITY = 200;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ConcurrentLinkedDeque<Execution> records = new ConcurrentLinkedDeque<>();

    private final AtomicLong seq = new AtomicLong();

    public void record(String taskName, LocalDateTime startTime, long costMs, boolean success) {
        records.addFirst(new Execution(
                seq.incrementAndGet(), taskName, startTime.format(FMT),
                Thread.currentThread().getName(), costMs, success));
        while (records.size() > CAPACITY) {
            records.pollLast();
        }
    }

    /** 最近的执行记录，按时间倒序 */
    public List<Execution> recent(int limit) {
        return records.stream().limit(limit).toList();
    }

    /** 按任务名汇总：执行次数、最近一次时间、平均耗时 */
    public List<Map<String, Object>> stats() {
        Map<String, List<Execution>> grouped = records.stream()
                .collect(Collectors.groupingBy(Execution::taskName, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((name, list) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("任务名", name);
            item.put("执行次数", list.size());
            item.put("最近执行", list.get(0).time());
            item.put("平均耗时ms", Math.round(list.stream().mapToLong(Execution::costMs).average().orElse(0)));
            item.put("执行线程", list.get(0).thread());
            result.add(item);
        });
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("任务名"))));
        return result;
    }

    /**
     * 一条执行记录
     */
    public record Execution(long id, String taskName, String time, String thread, long costMs, boolean success) {
    }
}
