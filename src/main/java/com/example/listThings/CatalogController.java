package com.example.listThings;

import com.example.listThings.service.CategoryService;
import com.example.listThings.service.CategoryService.CatalogCategory;
import com.example.listThings.service.ItemService;
import com.example.listThings.service.ItemService.PackingItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CategoryService categoryService;
    private final ItemService itemService;

    public CatalogController(CategoryService categoryService, ItemService itemService) {
        this.categoryService = categoryService;
        this.itemService = itemService;
    }

    @GetMapping("/categories")
    public List<CatalogCategory> categories() {
        return categoryService.catalogCategories();
    }

    @GetMapping("/items")
    public List<PackingItem> items(@RequestParam(required = false) List<Long> categoryIds) {
        return itemService.packingItems(categoryIds);
    }
}
