package top.mqxu.doc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.mqxu.doc.entity.Category;

/**
 * 图书列表查询条件
 *
 * @author mqxu
 */
@Data
@Schema(description = "图书查询条件")
public class BookQuery {

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，最大 100", example = "10", defaultValue = "10")
    private Integer pageSize = 10;

    @Schema(description = "书名或作者关键字，模糊匹配", example = "Java")
    private String keyword;

    @Schema(description = "分类过滤，不传表示全部", example = "TECH")
    private Category category;
}
