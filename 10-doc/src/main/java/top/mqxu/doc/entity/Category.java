package top.mqxu.doc.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图书分类。枚举值会出现在文档的下拉框里，调用方不用猜。
 *
 * @author mqxu
 */
@Schema(description = "图书分类")
public enum Category {

    @Schema(description = "计算机与技术") TECH,
    @Schema(description = "文学") LITERATURE,
    @Schema(description = "历史") HISTORY,
    @Schema(description = "科普") SCIENCE,
    @Schema(description = "其他") OTHER
}
