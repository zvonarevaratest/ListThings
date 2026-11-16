package com.example.listThings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final JdbcTemplate jdbc;

    public CatalogController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Category(long id, String name, String type) {}
    public record PackingItem(long id, String name) {}

    @GetMapping("/categories")
    public List<Category> categories() {
        return jdbc.query("""
                SELECT id, name, category_type FROM categories
                ORDER BY FIELD(category_type, 'TRANSPORT', 'ACCOMMODATION', 'UNIVERSAL'), name
                """, (rs, rowNum) -> new Category(rs.getLong("id"), rs.getString("name"), rs.getString("category_type")));
    }

    @GetMapping("/items")
    public List<PackingItem> items(@RequestParam List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбери хотя бы один транспорт");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(categoryIds.size(), "?"));
        Integer transportCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE category_type = 'TRANSPORT' AND id IN (" + placeholders + ")",
                Integer.class,
                categoryIds.toArray());
        if (transportCount == null || transportCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбери хотя бы один транспорт");
        }
        String sql = """
                SELECT DISTINCT i.id, i.name
                FROM items i
                JOIN item_categories ic ON ic.item_id = i.id
                JOIN categories c ON c.id = ic.category_id
                WHERE c.category_type = 'UNIVERSAL'
                   OR c.id IN (%s)
                ORDER BY i.name
                """.formatted(placeholders);
        return jdbc.query(sql, (rs, rowNum) -> new PackingItem(rs.getLong("id"), rs.getString("name")), categoryIds.toArray());
    }
}
