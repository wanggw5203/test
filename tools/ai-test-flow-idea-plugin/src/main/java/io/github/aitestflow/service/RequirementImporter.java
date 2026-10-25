package io.github.aitestflow.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class RequirementImporter {
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 600_000;

    public record ImageAttachment(String name, String mimeType, String dataUrl) {}
    public record ImportedMaterial(String text, List<ImageAttachment> images, List<Path> sources) {}

    public ImportedMaterial read(List<Path> files) throws IOException {
        if (files == null || files.isEmpty()) throw new IOException("未选择需求材料");
        StringBuilder text = new StringBuilder();
        List<ImageAttachment> images = new ArrayList<>();
        List<Path> sources = new ArrayList<>();
        for (Path original : files) {
            Path file = original.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) throw new IOException("文件不存在: " + file);
            sources.add(file);
            String extension = extension(file);
            text.append("\n\n===== 来源: ").append(file.getFileName()).append(" =====\n");
            switch (extension) {
                case "md", "markdown", "txt", "yaml", "yml", "json" -> text.append(Files.readString(file, StandardCharsets.UTF_8));
                case "docx" -> text.append(readDocx(file));
                case "doc" -> text.append(readDoc(file));
                case "pdf" -> text.append(readPdf(file));
                case "html", "htm" -> text.append(Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name()).text());
                case "png", "jpg", "jpeg", "webp" -> {
                    images.add(readImage(file, extension));
                    text.append("[原型/需求图片已作为视觉附件发送给模型: ").append(file.getFileName()).append("]");
                }
                default -> throw new IOException("暂不支持的需求材料格式: ." + extension + "（支持 MD/TXT/Word/PDF/HTML/PNG/JPG/WEBP）");
            }
            if (text.length() > MAX_TEXT_CHARS) {
                throw new IOException("需求材料文本超过 " + MAX_TEXT_CHARS + " 字符，请拆分后导入");
            }
        }
        return new ImportedMaterial(text.toString().trim(), List.copyOf(images), List.copyOf(sources));
    }

    private static String readDocx(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file); XWPFDocument document = new XWPFDocument(input)) {
            StringBuilder result = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> result.append(paragraph.getText()).append('\n'));
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> {
                    result.append("| ");
                    row.getTableCells().forEach(cell -> result.append(cell.getText().replace('\n', ' ')).append(" | "));
                    result.append('\n');
                });
            }
            return result.toString();
        }
    }

    private static String readDoc(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file); HWPFDocument document = new HWPFDocument(input)) {
            return document.getRange().text();
        }
    }

    private static String readPdf(Path file) throws IOException {
        try (var document = Loader.loadPDF(file.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static ImageAttachment readImage(Path file, String extension) throws IOException {
        long size = Files.size(file);
        if (size > MAX_IMAGE_BYTES) throw new IOException("图片超过 12MB: " + file.getFileName());
        String mime = switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
        String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        return new ImageAttachment(file.getFileName().toString(), mime, "data:" + mime + ";base64," + encoded);
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
