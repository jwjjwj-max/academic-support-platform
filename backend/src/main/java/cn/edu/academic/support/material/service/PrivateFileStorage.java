package cn.edu.academic.support.material.service;

import cn.edu.academic.support.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PrivateFileStorage {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");
    private final cn.edu.academic.support.storage.ObjectStorage objects;
    private final long maxBytes;
    private final long gradeMaxBytes;
    private final long moralMaxBytes;

    public PrivateFileStorage(cn.edu.academic.support.storage.ObjectStorage objects,
                              @Value("${app.storage.photo-max-bytes}") long maxBytes,
                              @Value("${app.storage.grade-max-bytes}") long gradeMaxBytes,
                              @Value("${app.storage.moral-max-bytes}") long moralMaxBytes) {
        this.objects=objects; this.maxBytes=maxBytes; this.gradeMaxBytes=gradeMaxBytes; this.moralMaxBytes=moralMaxBytes;
    }

    public StoredPhoto storePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("PHOTO_REQUIRED", "请选择照片文件");
        if (file.getSize() > maxBytes) throw new BusinessException("PHOTO_TOO_LARGE", "单张照片不能超过5MB");
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extension(original);
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String detected = detect(file);
        if (!TYPES.contains(declared) || !declared.equals(detected)
                || !("jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension))) {
            throw new BusinessException("PHOTO_TYPE_INVALID", "仅支持内容真实的 JPG、JPEG 或 PNG 照片");
        }
        validateImage(file);
        String key = "photos/" + UUID.randomUUID() + ("image/png".equals(detected) ? ".png" : ".jpg");
        save(key,file,detected);
        return new StoredPhoto(key, original, detected, file.getSize());
    }

    public byte[] read(String key) {
        try {
            long size=objects.size(key);
            long limit=Math.max(maxBytes,Math.max(gradeMaxBytes,moralMaxBytes));
            if(size<1 || size>limit) throw new IOException("Invalid stored size");
            try(InputStream in=objects.open(key)) {
                byte[] bytes=in.readNBytes(Math.toIntExact(size)+1);
                if(bytes.length!=size) throw new IOException("Stored size mismatch");
                return bytes;
            }
        } catch(IOException e) { throw new BusinessException("PRIVATE_FILE_READ_FAILED","私有文件读取失败",HttpStatus.BAD_GATEWAY); }
    }
    public org.springframework.core.io.Resource resource(String key) {
        return new org.springframework.core.io.ByteArrayResource(read(key));
    }
    public String signedGet(String key) { return objects.signedGet(key); }
    private void save(String key,MultipartFile file,String mime) {
        try(InputStream in=file.getInputStream()) {
            objects.put(key,in,file.getSize(),mime);
            if(objects.size(key)!=file.getSize()) throw new IOException("Stored size mismatch");
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        public void afterCompletion(int status) {
                            if(status!=STATUS_COMMITTED) { try { objects.delete(key); } catch(RuntimeException ignored) { /* Reconcile orphan objects during maintenance. */ } }
                        }
                    });
        } catch(IOException e) {
            try { objects.delete(key); } catch(RuntimeException ignored) { /* Reconcile during maintenance. */ }
            throw new BusinessException("PRIVATE_FILE_STORE_FAILED","文件保存失败，请重试",HttpStatus.BAD_GATEWAY);
        }
    }

    public StoredFile storeGrade(MultipartFile file) {
        return storeDocument(file, "grades", gradeMaxBytes, Set.of("csv", "xls", "xlsx"), "GRADE");
    }

    public StoredFile storeMoral(MultipartFile file) {
        return storeDocument(file, "moral", moralMaxBytes, Set.of("csv", "xls", "xlsx", "pdf"), "MORAL");
    }

    private StoredFile storeDocument(MultipartFile file, String folder, long limit, Set<String> extensions, String kind) {
        if (file == null || file.isEmpty()) throw new BusinessException(kind + "_FILE_REQUIRED", "请选择文件");
        if (file.getSize() > limit) throw new BusinessException(kind + "_FILE_TOO_LARGE", "单个文件不能超过20MB");
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extension(original);
        if (!extensions.contains(extension)) throw new BusinessException(kind + "_FILE_TYPE_INVALID", "文件扩展名不受支持");
        String detected = detectDocument(file, extension);
        if("xls".equals(extension)||"xlsx".equals(extension)) {
            try(InputStream in=file.getInputStream(); var workbook=org.apache.poi.ss.usermodel.WorkbookFactory.create(in)) {
                if(workbook.getNumberOfSheets()==0) throw new IOException("Empty workbook");
            } catch(Exception e) {throw new BusinessException("FILE_CONTENT_INVALID","Excel 文件内容无效或已损坏");}
        }
        if("csv".equals(extension)) {
            try(InputStream in=file.getInputStream()) {
                byte[] bytes=in.readNBytes(Math.toIntExact(limit)+1);
                if(bytes.length>limit) throw new IOException("Too large");
                for(byte value:bytes) if(value==0) throw new IOException("Binary data");
                try { java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)); }
                catch(java.nio.charset.CharacterCodingException ignored) { java.nio.charset.Charset.forName("GB18030").newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)); }
            } catch(IOException e) {throw new BusinessException("FILE_CONTENT_INVALID","CSV 文件不是有效文本");}
        }
        String key = folder + "/" + UUID.randomUUID() + "." + extension;
        save(key,file,detected);
        return new StoredFile(key, original, detected, file.getSize(), extension);
    }

    private String detectDocument(MultipartFile file, String extension) {
        try (InputStream in = file.getInputStream()) {
            byte[] b = in.readNBytes(8);
            if ("pdf".equals(extension)) {
                if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
                throw new BusinessException("FILE_CONTENT_INVALID", "PDF 文件内容与扩展名不一致");
            }
            if ("xlsx".equals(extension)) {
                if (b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4)
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                throw new BusinessException("FILE_CONTENT_INVALID", "XLSX 文件内容与扩展名不一致");
            }
            if ("xls".equals(extension)) {
                int[] signature = {0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1};
                for (int i = 0; i < signature.length; i++) if (b.length <= i || (b[i] & 255) != signature[i])
                    throw new BusinessException("FILE_CONTENT_INVALID", "XLS 文件内容与扩展名不一致");
                return "application/vnd.ms-excel";
            }
            if ("csv".equals(extension)) {
                for (byte value : b) if (value == 0) throw new BusinessException("FILE_CONTENT_INVALID", "CSV 文件不是有效文本");
                return "text/csv";
            }
            throw new BusinessException("FILE_CONTENT_INVALID", "无法识别文件内容");
        } catch (IOException e) {
            throw new BusinessException("FILE_READ_FAILED", "无法读取文件内容");
        }
    }

    private void validateImage(MultipartFile file) {
        try(InputStream in=file.getInputStream(); var image=javax.imageio.ImageIO.createImageInputStream(in)) {
            var readers=javax.imageio.ImageIO.getImageReaders(image);
            if(!readers.hasNext()) throw new IOException("Invalid image");
            var reader=readers.next();
            try {
                reader.setInput(image);
                long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                if(pixels<1 || pixels>25000000 || reader.read(0)==null) throw new IOException("Invalid image dimensions");
            } finally {reader.dispose();}
        } catch(IOException | RuntimeException e) {throw new BusinessException("PHOTO_TYPE_INVALID","照片无法解码或尺寸过大");}
    }

    private String detect(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] b = in.readNBytes(8);
            if (b.length >= 3 && (b[0] & 255) == 0xff && (b[1] & 255) == 0xd8 && (b[2] & 255) == 0xff) return "image/jpeg";
            if (b.length == 8 && (b[0] & 255) == 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47
                    && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) return "image/png";
            return "application/octet-stream";
        } catch (IOException e) { throw new BusinessException("PHOTO_READ_FAILED", "无法读取照片内容"); }
    }

    private String safeOriginalName(String name) {
        String value = name == null ? "photo" : Paths.get(name).getFileName().toString().replaceAll("[\\r\\n]", "");
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }
    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    public record StoredPhoto(String key, String originalFilename, String mimeType, long size) {}
    public record StoredFile(String key, String originalFilename, String mimeType, long size, String extension) {}
}
