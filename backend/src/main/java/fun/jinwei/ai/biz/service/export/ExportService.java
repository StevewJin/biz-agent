package fun.jinwei.ai.biz.service.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 导出服务：将结构化数据导出为 xlsx / csv 并返回下载地址。
 *
 * <p>由内置扩展包 builtin_export 的 export 工具调用。</p>
 */
@Slf4j
@Service
public class ExportService {

    private final ObjectMapper objectMapper;
    private final Path exportDir;

    public ExportService(ObjectMapper objectMapper,
                         @Value("${app.export.dir:}") String exportPath) {
        this.objectMapper = objectMapper;
        this.exportDir = resolveExportDir(exportPath);
        log.info("导出目录: {}", exportDir);
    }

    /** 依次尝试配置目录 → 数据目录 → 系统临时目录，选出第一个可写的 */
    private Path resolveExportDir(String configured) {
        List<Path> candidates = new java.util.ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Paths.get(configured).toAbsolutePath().normalize());
        }
        candidates.add(Paths.get(System.getProperty("java.io.tmpdir"), "bizagent", "exports")
                .toAbsolutePath().normalize());
        for (Path candidate : candidates) {
            try {
                Files.createDirectories(candidate);
                if (Files.isWritable(candidate)) {
                    return candidate;
                }
            } catch (IOException | SecurityException e) {
                log.warn("导出目录不可用，尝试下一个: {} ({})", candidate, e.getMessage());
            }
        }
        throw new IllegalStateException("无可写的导出目录，请配置 app.export.dir");
    }

    public record ExportArtifact(String downloadUrl, String filename, String format,
                                 int rowCount, long fileSize, String fileSizeFormatted) {
    }

    /** 执行导出 */
    public ExportArtifact export(JsonNode params, Long conversationId) throws IOException {
        String filename = params.has("filename") ? params.get("filename").asText().trim() : "";
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename 不能为空");
        }
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        String format = params.has("format") ? params.get("format").asText().toLowerCase() : "xlsx";
        if (!Set.of("xlsx", "csv").contains(format)) {
            throw new IllegalArgumentException("不支持的格式: " + format + "，支持 xlsx / csv");
        }
        if (!params.has("headers") || !params.get("headers").isArray() || params.get("headers").isEmpty()) {
            throw new IllegalArgumentException("headers 必须是非空数组");
        }
        if (!params.has("rows") || !params.get("rows").isArray()) {
            throw new IllegalArgumentException("rows 必须是数组");
        }

        ArrayNode headersNode = (ArrayNode) params.get("headers");
        ArrayNode rowsNode = (ArrayNode) params.get("rows");

        String[] headers = new String[headersNode.size()];
        for (int i = 0; i < headersNode.size(); i++) {
            headers[i] = headersNode.get(i).asText();
        }

        ensureExportDirExists();
        String uniqueName = conversationId + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "_" + filename + "." + format;
        Path filePath = exportDir.resolve(uniqueName);

        int rowCount = "csv".equals(format)
                ? writeCsv(filePath, headers, rowsNode)
                : writeXlsx(filePath, headers, rowsNode);
        long fileSize = Files.size(filePath);

        log.info("文件导出成功: {} ({} 行, {})", uniqueName, rowCount, formatFileSize(fileSize));
        return new ExportArtifact("/api/exports/" + uniqueName, filename + "." + format, format,
                rowCount, fileSize, formatFileSize(fileSize));
    }

    /** 将行数据转换为前端下载卡片需要的渲染参数 */
    public Map<String, Object> toRenderProps(ExportArtifact artifact) {
        return Map.of(
                "downloadUrl", artifact.downloadUrl(),
                "filename", artifact.filename(),
                "format", artifact.format(),
                "rowCount", artifact.rowCount(),
                "fileSize", artifact.fileSize(),
                "fileSizeFormatted", artifact.fileSizeFormatted());
    }

    public String toJson(ExportArtifact artifact) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("downloadUrl", artifact.downloadUrl());
        node.put("filename", artifact.filename());
        node.put("format", artifact.format());
        node.put("rowCount", artifact.rowCount());
        node.put("fileSize", artifact.fileSize());
        node.put("fileSizeFormatted", artifact.fileSizeFormatted());
        return node.toString();
    }

    private int writeXlsx(Path filePath, String[] headers, ArrayNode rowsNode) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(headerStyle);

            CellStyle dataStyle = workbook.createCellStyle();
            applyBorder(dataStyle);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowCount = 0;
            for (int r = 0; r < rowsNode.size(); r++) {
                JsonNode rowNode = rowsNode.get(r);
                if (!rowNode.isArray()) continue;
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < Math.min(rowNode.size(), headers.length); c++) {
                    Cell cell = row.createCell(c);
                    JsonNode val = rowNode.get(c);
                    if (val.isNumber()) {
                        cell.setCellValue(val.asDouble());
                    } else {
                        cell.setCellValue(val.asText());
                    }
                    cell.setCellStyle(dataStyle);
                }
                rowCount++;
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1024, 256 * 40));
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
            return rowCount;
        }
    }

    private void applyBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private int writeCsv(Path filePath, String[] headers, ArrayNode rowsNode) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(String.join(",", escapeCsv(headers))).append("\n");

        int rowCount = 0;
        for (int r = 0; r < rowsNode.size(); r++) {
            JsonNode rowNode = rowsNode.get(r);
            if (!rowNode.isArray()) continue;
            String[] values = new String[Math.min(rowNode.size(), headers.length)];
            for (int c = 0; c < values.length; c++) {
                values[c] = rowNode.get(c).asText();
            }
            sb.append(String.join(",", escapeCsv(values))).append("\n");
            rowCount++;
        }
        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
        return rowCount;
    }

    private String[] escapeCsv(String[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                result[i] = "\"" + v.replace("\"", "\"\"") + "\"";
            } else {
                result[i] = v;
            }
        }
        return result;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void ensureExportDirExists() throws IOException {
        if (!Files.exists(exportDir)) {
            Files.createDirectories(exportDir);
        }
    }
}
