package top.mqxu.doc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.doc.common.BusinessException;
import top.mqxu.doc.common.ErrorCode;
import top.mqxu.doc.common.PageResult;
import top.mqxu.doc.common.Result;
import top.mqxu.doc.dto.BookCreateRequest;
import top.mqxu.doc.dto.BookQuery;
import top.mqxu.doc.entity.Book;
import top.mqxu.doc.entity.Category;
import top.mqxu.doc.service.BookService;

import java.util.List;

/**
 * 图书接口。
 * <p>
 * 注解的作用是把"代码里的契约"暴露到文档上：@Tag 分组、@Operation 描述用途、
 * @Parameter 说明参数、@ApiResponse 列出可能的响应。写得细一点，前端就不用过来问。
 *
 * @author mqxu
 */
@Tag(name = "图书管理", description = "图书的增删改查、分页与分类查询")
@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @Operation(
            summary = "分页查询图书",
            description = "支持按关键字（书名或作者）与分类过滤。两个条件都可省略，不传即查全部。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "分页参数非法",
                    content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @GetMapping
    public Result<PageResult<Book>> page(@ParameterObject BookQuery query) {
        return Result.ok(bookService.page(query));
    }

    @Operation(summary = "按 ID 查询图书", description = "图书不存在时返回 404，业务码 20404")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "图书不存在",
                    content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @GetMapping("/{id}")
    public Result<Book> getById(
            @Parameter(description = "图书 ID", example = "1", required = true)
            @PathVariable Long id) {
        return Result.ok(bookService.getById(id));
    }

    @Operation(summary = "新增图书", description = "ISBN 全局唯一，重复时返回 409")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "新增成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败",
                    content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "409", description = "ISBN 已存在",
                    content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @PostMapping
    public Result<Book> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "图书信息，除库存外都是必填",
                    required = true,
                    content = @Content(examples = @ExampleObject(name = "标准示例", value = """
                            {
                              "title": "重构：改善既有代码的设计",
                              "author": "Martin Fowler",
                              "isbn": "9787115508645",
                              "category": "TECH",
                              "price": 118.00,
                              "stock": 15,
                              "publishDate": "2019-05-01"
                            }""")))
            @Valid @RequestBody BookCreateRequest request) {
        return Result.ok(bookService.create(request));
    }

    @Operation(summary = "更新图书", description = "全量更新，未传的字段会被覆盖成 null 或默认值")
    @PutMapping("/{id}")
    public Result<Book> update(
            @Parameter(description = "图书 ID", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody BookCreateRequest request) {
        return Result.ok(bookService.update(id, request));
    }

    @Operation(summary = "删除图书", description = "物理删除；生产上更常见的是软删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "图书 ID", example = "1", required = true)
            @PathVariable Long id) {
        bookService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "查询全部分类", description = "枚举值，前端下拉框直接用这个接口的返回渲染")
    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(bookService.categories());
    }

    /**
     * 演示用：强制抛出一个未预期异常，观察 500 响应在文档与监控里的样子。
     */
    @Operation(summary = "触发未预期异常", description = "演示 500 响应的结构，生产环境不会保留这种接口")
    @GetMapping("/boom")
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> boom() {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
