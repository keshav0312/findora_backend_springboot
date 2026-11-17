package com.findora.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.findora.config.AppProperties;
import com.findora.web.ApiException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Stores report photos and chat attachments in Cloudinary. */
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

    private final Cloudinary cloudinary;
    private final boolean cloudinaryConfigured;

    public FileStorageService(AppProperties props) {
        AppProperties.Cloudinary config = props.cloudinary();
        this.cloudinaryConfigured = !isBlank(config.cloudName())
                && !isBlank(config.apiKey())
                && !isBlank(config.apiSecret());
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", config.cloudName(),
                "api_key", config.apiKey(),
                "api_secret", config.apiSecret(),
                "secure", true));
    }

    /** Report photo: jpeg/jpg/png/webp/gif, max 5 MB. Returns its secure Cloudinary URL. */
    public String storeImage(MultipartFile file) {
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !IMAGE_EXT.contains(ext)) throw ApiException.badRequest("Only image files are allowed");
        if (file.getSize() > MAX_PHOTO_BYTES) throw ApiException.badRequest("File too large");
        return upload(file, "findora/reports");
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
        return upload(file, "findora/chat-attachments");
    }

    private String upload(MultipartFile file, String folder) {
        if (!cloudinaryConfigured) {
            throw ApiException.serviceUnavailable(
                    "Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto",
                    "use_filename", true,
                    "unique_filename", true,
                    "overwrite", false));
            Object secureUrl = result.get("secure_url");
            if (secureUrl instanceof String url && !url.isBlank()) return url;
            throw new IllegalStateException("Cloudinary upload returned no secure URL");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded file", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String extensionOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
