package top.mqxu.toolkit.controller;

import cn.hutool.system.oshi.CpuInfo;
import cn.hutool.system.oshi.OshiUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.SystemInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * 案例 10：系统状态面板
 *
 * @author mqxu
 * @date 2026-10-21
 * @description OshiUtil 读硬件，ManagementFactory 读 JVM，合成一份状态
 */
@Slf4j
@RestController
@RequestMapping("/toolkit/system")
public class SystemController {

    @GetMapping
    public Result<SystemInfo> info() {
        SystemInfo.Cpu cpu = readCpu();
        SystemInfo.Memory memory = readMemory();
        List<SystemInfo.Disk> disks = readDisks();
        SystemInfo.Jvm jvm = readJvm();
        SystemInfo.Os os = readOs();
        return Result.ok(new SystemInfo(cpu, memory, disks, jvm, os));
    }

    private SystemInfo.Cpu readCpu() {
        try {
            // 默认采样 1 秒，接口会慢一点，换来的是真实使用率
            CpuInfo info = OshiUtil.getCpuInfo();
            double usage = Math.max(0, Math.min(100, 100 - info.getFree()));
            return new SystemInfo.Cpu(info.getCpuModel(), info.getCpuNum(), round(usage));
        } catch (Exception e) {
            log.warn("读取 CPU 信息失败：{}", e.getMessage());
            return new SystemInfo.Cpu("unknown", Runtime.getRuntime().availableProcessors(), 0);
        }
    }

    private SystemInfo.Memory readMemory() {
        var memory = OshiUtil.getMemory();
        long total = memory.getTotal();
        long used = total - memory.getAvailable();
        return new SystemInfo.Memory(total, used, round(percent(used, total)));
    }

    private List<SystemInfo.Disk> readDisks() {
        List<SystemInfo.Disk> disks = new ArrayList<>();
        OshiUtil.getOs().getFileSystem().getFileStores().forEach(store -> {
            long total = store.getTotalSpace();
            if (total <= 0) {
                return;
            }
            long used = total - store.getUsableSpace();
            disks.add(new SystemInfo.Disk(store.getName(), store.getMount(),
                    total, used, round(percent(used, total))));
        });
        return disks;
    }

    private SystemInfo.Jvm readJvm() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = runtime.maxMemory();
        return new SystemInfo.Jvm(heapUsed, heapMax, threadBean.getThreadCount(),
                System.getProperty("java.version"), runtimeBean.getUptime() / 1000);
    }

    private SystemInfo.Os readOs() {
        return new SystemInfo.Os(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                OshiUtil.getOs().getSystemUptime());
    }

    private double percent(long used, long total) {
        return total <= 0 ? 0 : used * 100.0 / total;
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
