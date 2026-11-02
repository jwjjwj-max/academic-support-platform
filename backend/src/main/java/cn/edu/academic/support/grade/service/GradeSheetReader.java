package cn.edu.academic.support.grade.service;

import cn.edu.academic.support.common.exception.BusinessException;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

@Component
public class GradeSheetReader {
    public SheetData read(byte[] bytes, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            return lower.endsWith(".csv") ? readCsv(bytes) : readExcel(bytes);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("GRADE_FILE_PARSE_FAILED", "成绩单解析失败，请确认文件未损坏且首行为表头"); }
    }

    private SheetData readExcel(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw new BusinessException("GRADE_HEADER_MISSING", "成绩单缺少表头");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<String> headers = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String header = formatter.formatCellValue(headerRow.getCell(c)).trim();
                if (header.isBlank()) header = "未命名列" + (c + 1);
                if (!seen.add(header)) throw new BusinessException("GRADE_HEADER_DUPLICATE", "成绩单存在重复表头：" + header);
                headers.add(header);
            }
            List<SheetRow> rows = new ArrayList<>();
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r); if (row == null) continue;
                Map<String,String> values = new LinkedHashMap<>(); boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String value = formatter.formatCellValue(row.getCell(c)).trim(); any |= !value.isBlank(); values.put(headers.get(c), value);
                }
                if (any) rows.add(new SheetRow(r + 1, values));
            }
            return new SheetData(headers, rows);
        }
    }

    private SheetData readCsv(byte[] bytes) throws IOException {
        String text = decode(bytes);
        try (Reader reader = new StringReader(text); var parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get().parse(reader)) {
            List<String> headers = parser.getHeaderNames().stream().map(String::trim).toList();
            if (headers.isEmpty()) throw new BusinessException("GRADE_HEADER_MISSING", "成绩单缺少表头");
            if (new HashSet<>(headers).size() != headers.size()) throw new BusinessException("GRADE_HEADER_DUPLICATE", "CSV 存在重复表头");
            List<SheetRow> rows = new ArrayList<>();
            for (var record : parser) {
                Map<String,String> values = new LinkedHashMap<>(); boolean any = false;
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < record.size() ? record.get(i).trim() : ""; any |= !value.isBlank(); values.put(headers.get(i), value);
                }
                if (any) rows.add(new SheetRow((int) record.getRecordNumber() + 1, values));
            }
            return new SheetData(headers, rows);
        }
    }

    private String decode(byte[] bytes) throws CharacterCodingException {
        if (bytes.length >= 3 && (bytes[0]&255)==0xef && (bytes[1]&255)==0xbb && (bytes[2]&255)==0xbf)
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException ignored) { return Charset.forName("GB18030").newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
    }

    public record SheetData(List<String> headers,List<SheetRow> rows) {}
    public record SheetRow(int rowNo,Map<String,String> values) {}
}
