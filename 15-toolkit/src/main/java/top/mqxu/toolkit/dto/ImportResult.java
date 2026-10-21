package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 导入结果
 *
 * @param total        总行数
 * @param success      成功行数
 * @param failed       失败行数
 * @param errors       错误明细
 * @param errorFileUrl 错误明细文件下载地址，没有错误时为 null
 * @author mqxu
 * @date 2026-10-21
 */
public record ImportResult(int total, int success, int failed,
                           List<RowError> errors, String errorFileUrl) {

    public record RowError(int row, String orderNo, String message) {
    }
}
