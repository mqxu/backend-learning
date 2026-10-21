package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 敏感词过滤结果
 *
 * @param hits        命中的词
 * @param filtered    替换成星号后的文本
 * @param highlighted 用 mark 标签包起来的文本，前端直接渲染
 * @author mqxu
 * @date 2026-10-21
 */
public record SensitiveResult(List<String> hits, String filtered, String highlighted) {
}
