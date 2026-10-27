package top.mqxu.security.model;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务状态。文本给前端直接展示，避免前端再写一遍中文映射
 **/
public enum TaskStatus {

    SUBMITTED("待批改"),
    REVIEWED("已批改");

    private final String text;

    TaskStatus(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
