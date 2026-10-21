package top.mqxu.toolkit.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.ImportResult;
import top.mqxu.toolkit.dto.OrderRow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入导出
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 导出带样式，导入逐行校验并生成标红的错误明细
 */
@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final List<String> HEADERS =
            List.of("订单号", "客户", "手机号", "金额", "状态", "下单时间");
    private static final List<String> STATUSES = List.of("待支付", "已支付", "已取消");

    private final ToolkitProperties properties;

    /**
     * 导出订单报表
     */
    public byte[] export() {
        List<Map<String, Object>> rows = demoData();
        try (ExcelWriter writer = ExcelUtil.getWriter(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writer.renameSheet("订单");
            writer.writeHeadRow(HEADERS);
            styleHeader(writer);
            writer.write(rows, false);

            // 金额列保留两位小数
            CellStyle moneyStyle = writer.getWorkbook().createCellStyle();
            moneyStyle.setDataFormat(writer.getWorkbook().createDataFormat().getFormat("#,##0.00"));
            writer.setColumnStyleIfHasData(3, 1, moneyStyle);

            colorStatusColumn(writer, rows.size());
            writer.setFreezePane(1);
            setColumnWidths(writer);
            addStatusValidation(writer.getSheet());

            writer.flush(out, true);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败：" + e.getMessage());
        }
    }

    /**
     * 导入模板：表头 + 一行示例 + 状态下拉
     */
    public byte[] template() {
        try (ExcelWriter writer = ExcelUtil.getWriter(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writer.renameSheet("订单导入");
            writer.writeHeadRow(HEADERS);
            styleHeader(writer);

            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("订单号", "SO20261021001");
            sample.put("客户", "张三");
            sample.put("手机号", "13800001001");
            sample.put("金额", new BigDecimal("199.00"));
            sample.put("状态", "待支付");
            sample.put("下单时间", "2026-10-21 10:00:00");
            writer.write(List.of(sample), false);

            writer.setFreezePane(1);
            addStatusValidation(writer.getSheet());
            setColumnWidths(writer);

            writer.flush(out, true);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败：" + e.getMessage());
        }
    }

    /**
     * 导入并校验，失败行写进一份标红的 Excel
     */
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        List<OrderRow> rows;
        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            rows = reader.readAll(OrderRow.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXCEL_READ_FAILED,
                    "无法解析文件，请使用 .xlsx 格式：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.EXCEL_EMPTY);
        }

        List<ImportResult.RowError> errors = new ArrayList<>();
        Set<String> seenOrderNos = new HashSet<>();
        int total = 0;
        int success = 0;

        for (int i = 0; i < rows.size(); i++) {
            OrderRow row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            total++;
            // Excel 第 1 行是表头，数据从第 2 行开始
            int lineNo = i + 2;
            String message = validate(row, seenOrderNos);
            if (message == null) {
                success++;
            } else {
                errors.add(new ImportResult.RowError(lineNo, row.getOrderNo(), message));
            }
        }

        String errorFileUrl = errors.isEmpty() ? null : writeErrorReport(errors);
        return new ImportResult(total, success, errors.size(), errors, errorFileUrl);
    }

    /**
     * 下载错误明细，文件名做了白名单校验，避免路径穿越
     */
    public File errorFile(String fileName) {
        if (StrUtil.isBlank(fileName)
                || !fileName.startsWith("import-error-")
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        File file = new File(properties.getWorkDir(), fileName);
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被清理");
        }
        return file;
    }

    private String validate(OrderRow row, Set<String> seenOrderNos) {
        if (StrUtil.isBlank(row.getOrderNo())) {
            return "订单号不能为空";
        }
        if (!seenOrderNos.add(row.getOrderNo().trim())) {
            return "订单号重复：" + row.getOrderNo();
        }
        if (StrUtil.isBlank(row.getCustomer())) {
            return "客户不能为空";
        }
        if (!Validator.isMobile(StrUtil.trim(row.getPhone()))) {
            return "手机号格式不正确：" + StrUtil.nullToEmpty(row.getPhone());
        }

        BigDecimal amount = parseAmount(row.getAmount());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "金额必须是大于 0 的数字：" + StrUtil.nullToEmpty(row.getAmount());
        }
        if (!STATUSES.contains(StrUtil.trim(row.getStatus()))) {
            return "状态只能是待支付、已支付、已取消：" + StrUtil.nullToEmpty(row.getStatus());
        }
        if (!isDateTime(row.getCreatedAt())) {
            return "下单时间格式应为 yyyy-MM-dd HH:mm:ss：" + StrUtil.nullToEmpty(row.getCreatedAt());
        }
        return null;
    }

    private BigDecimal parseAmount(String value) {
        try {
            return Convert.toBigDecimal(StrUtil.trim(value));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDateTime(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        try {
            DateUtil.parse(value, DatePattern.NORM_DATETIME_PATTERN);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlankRow(OrderRow row) {
        return StrUtil.isBlank(row.getOrderNo())
                && StrUtil.isBlank(row.getCustomer())
                && StrUtil.isBlank(row.getPhone())
                && StrUtil.isBlank(row.getAmount())
                && StrUtil.isBlank(row.getStatus())
                && StrUtil.isBlank(row.getCreatedAt());
    }

    private String writeErrorReport(List<ImportResult.RowError> errors) {
        File dir = FileUtil.mkdir(properties.getWorkDir());
        String fileName = "import-error-" + IdUtil.fastSimpleUUID() + ".xlsx";
        File file = new File(dir, fileName);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ImportResult.RowError error : errors) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("行号", error.row());
            row.put("订单号", StrUtil.nullToEmpty(error.orderNo()));
            row.put("原因", error.message());
            rows.add(row);
        }

        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            writer.renameSheet("错误明细");
            writer.writeHeadRow(List.of("行号", "订单号", "原因"));
            styleHeader(writer);
            writer.write(rows, false);

            CellStyle red = writer.getWorkbook().createCellStyle();
            Font font = writer.createFont();
            font.setColor(IndexedColors.RED.getIndex());
            red.setFont(font);
            for (int i = 1; i <= rows.size(); i++) {
                Row row = writer.getSheet().getRow(i);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        cell.setCellStyle(red);
                    }
                }
            }

            writer.setColumnWidth(0, 8);
            writer.setColumnWidth(1, 22);
            writer.setColumnWidth(2, 56);
            writer.flush(file);
        }
        return "/toolkit/excel/download/" + fileName;
    }

    private void styleHeader(ExcelWriter writer) {
        CellStyle style = writer.getHeadCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = writer.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
    }

    private void colorStatusColumn(ExcelWriter writer, int dataSize) {
        Map<String, CellStyle> styles = new HashMap<>();
        styles.put("已支付", fillStyle(writer, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN));
        styles.put("待支付", fillStyle(writer, IndexedColors.LIGHT_ORANGE, IndexedColors.DARK_ORANGE));
        styles.put("已取消", fillStyle(writer, IndexedColors.GREY_25_PERCENT, IndexedColors.GREY_50_PERCENT));

        Sheet sheet = writer.getSheet();
        for (int i = 1; i <= dataSize; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(4);
            if (cell == null) {
                continue;
            }
            CellStyle style = styles.get(cell.getStringCellValue());
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private CellStyle fillStyle(ExcelWriter writer, IndexedColors background,
                                IndexedColors fontColor) {
        CellStyle style = writer.getWorkbook().createCellStyle();
        style.setFillForegroundColor(background.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = writer.createFont();
        font.setColor(fontColor.getIndex());
        style.setFont(font);
        return style;
    }

    private void setColumnWidths(ExcelWriter writer) {
        writer.setColumnWidth(0, 22);
        writer.setColumnWidth(1, 14);
        writer.setColumnWidth(2, 16);
        writer.setColumnWidth(3, 12);
        writer.setColumnWidth(4, 12);
        writer.setColumnWidth(5, 22);
    }

    private void addStatusValidation(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                helper.createExplicitListConstraint(STATUSES.toArray(new String[0]));
        // 第 5 列，第 2 到 201 行
        CellRangeAddressList range = new CellRangeAddressList(1, 200, 4, 4);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private List<Map<String, Object>> demoData() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("订单号", "SO20261021" + StrUtil.padPre(String.valueOf(i), 3, '0'));
            row.put("客户", "客户" + i);
            row.put("手机号", "1380000" + StrUtil.padPre(String.valueOf(1000 + i), 4, '0'));
            row.put("金额", new BigDecimal("999.00").add(new BigDecimal(i * 100L)));
            row.put("状态", STATUSES.get(i % 3));
            row.put("下单时间", DateUtil.format(
                    DateUtil.offsetDay(new Date(), -i), DatePattern.NORM_DATETIME_PATTERN));
            rows.add(row);
        }
        return rows;
    }
}
