package com.findora.web;

import com.findora.service.ItemKind;
import com.findora.service.ItemService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lost")
public class LostController extends ItemController {
    public LostController(ItemService items) {
        super(ItemKind.LOST, items);
    }
}
