package com.findora.web;

import com.findora.security.AuthUser;
import com.findora.service.ItemKind;
import com.findora.service.ItemService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/** Shared endpoints for /api/lost and /api/found (see LostController / FoundController). */
public abstract class ItemController {

    private final ItemKind kind;
    private final ItemService items;

    protected ItemController(ItemKind kind, ItemService items) {
        this.kind = kind;
        this.items = items;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> params) {
        return items.list(kind, params);
    }

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(items.mine(kind, user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOne(@PathVariable String id,
                                                      @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(items.getOne(kind, id, user));
    }

    /** multipart/form-data: text fields + up to 5 `photos` files (what the report form sends). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal AuthUser user,
                                                      @RequestParam Map<String, String> body,
                                                      @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        ItemService.CreateResult result = items.create(kind, user, body, photos);
        Map<String, Object> out = ApiResponse.body("Report submitted", com.findora.web.ApiMapper.item(
                result.item(), result.item().getOwner(), true));
        Map<String, Object> ordered = new LinkedHashMap<>(out);
        ordered.put("matches", result.matches());
        return ResponseEntity.status(201).body(ordered);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id, @AuthenticationPrincipal AuthUser user,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok("Report updated", items.update(kind, id, user, body));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable String id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok("Marked as returned/resolved", items.markResolved(kind, id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id, @AuthenticationPrincipal AuthUser user) {
        items.remove(kind, id, user);
        return ApiResponse.message("Report deleted");
    }
}
