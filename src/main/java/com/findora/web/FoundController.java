package com.findora.web;

import com.findora.service.ItemKind;
import com.findora.service.ItemService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/found")
public class FoundController extends ItemController {
    public FoundController(ItemService items) {
        super(ItemKind.FOUND, items);
    }
}
