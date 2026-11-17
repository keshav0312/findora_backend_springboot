package com.findora.service;

import com.findora.config.AppProperties;
import com.findora.web.ApiException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Local-disk storage under `uploads/` (same as multer's diskStorage). Files are served at /uploads/<name>. */
@Service
public class FileStorageService {

    private static final Set<String> IMAGE_EXT = Set.of("jpeg", "jpg", "png", "webp", "gif");
    private static final Set<String> CHAT_EXT =
            Set.of("jpeg", "jpg", "png", "webp", "gif", "webm", "ogg", "mp3", "m4a", "wav");
    /** Browser MediaRecorder blobs are sometimes uploaded without a useful filename. */
    private static final Map<String, String> MIME_TO_EXT = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"), Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"), Map.entry("audio/webm", "webm"), Map.entry("video/webm", "webm"),
            Map.entry("audio/ogg", "ogg"), Map.entry("audio/mpeg", "mp3"), Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/mp4", "m4a"), Map.entry("audio/x-m4a", "m4a"), Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"));

    public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    public static final long MAX_CHAT_BYTES = 10L * 1024 * 1024;

    private final Path dir;
    private final SecureRandom random = new SecureRandom();

    public FileStorageService(AppProperties props) {
        this.dir = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(dir);
    }

    /** Report photo: jpeg/jpg/png/webp/gif, max 5 MB. Returns the public URL path ("/uploads/..."). */
    public String storeImage(MultipartFile file) {
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !IMAGE_EXT.contains(ext)) throw ApiException.badRequest("Only image files are allowed");
        if (file.getSize() > MAX_PHOTO_BYTES) throw ApiException.badRequest("File too large");
        return save(file, ext);
    }

    /** Chat attachment: image or short audio note, max 10 MB. */
    public String storeChatAttachment(MultipartFile file) {
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !CHAT_EXT.contains(ext)) {
            String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            int semi = mime.indexOf(';'); // e.g. "audio/webm;codecs=opus"
            ext = MIME_TO_EXT.get(semi >= 0 ? mime.substring(0, semi) : mime);
        }
        if (ext == null) throw ApiException.badRequest("Only images or audio voice notes are allowed");
        if (file.getSize() > MAX_CHAT_BYTES) throw ApiException.badRequest("File too large");
        return save(file, ext);
    }

    private String save(MultipartFile file, String ext) {
        String name = System.currentTimeMillis() + "-" + random.nextInt(1_000_000_000) + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store upload", e);
        }
        return "/uploads/" + name;
    }

    private static String extensionOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
