package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 系统状态
 *
 * @param cpu    CPU
 * @param memory 物理内存
 * @param disks  磁盘
 * @param jvm    JVM
 * @param os     操作系统
 * @author mqxu
 * @date 2026-10-21
 */
public record SystemInfo(Cpu cpu, Memory memory, List<Disk> disks, Jvm jvm, Os os) {

    /**
     * @param model CPU 型号
     * @param cores 核心数
     * @param usage 使用率（百分比）
     */
    public record Cpu(String model, int cores, double usage) {
    }

    /**
     * @param total 总字节数
     * @param used  已用字节数
     * @param usage 使用率（百分比）
     */
    public record Memory(long total, long used, double usage) {
    }

    /**
     * @param name  卷名
     * @param mount 挂载点
     * @param total 总字节数
     * @param used  已用字节数
     * @param usage 使用率（百分比）
     */
    public record Disk(String name, String mount, long total, long used, double usage) {
    }

    /**
     * @param heapUsed       堆已用字节数
     * @param heapMax        堆上限字节数
     * @param threads        线程数
     * @param javaVersion    Java 版本
     * @param uptimeSeconds  运行时长（秒）
     */
    public record Jvm(long heapUsed, long heapMax, int threads,
                      String javaVersion, long uptimeSeconds) {
    }

    /**
     * @param name          系统名称
     * @param version       版本
     * @param arch          架构
     * @param uptimeSeconds 开机时长（秒）
     */
    public record Os(String name, String version, String arch, long uptimeSeconds) {
    }
}
