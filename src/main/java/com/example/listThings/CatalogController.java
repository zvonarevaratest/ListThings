
package com.example.listThings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final JdbcTemplate jdbc;

    public CatalogController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Category(long id, String name, String type) {
    }

    public record PackingItem(long id, String name) {
    }

    @GetMapping("/categories")
    public List<Category> categories() {

        return jdbc.query("""
                SELECT id, name, category_type
                FROM categories
                ORDER BY name
                """,
                (rs, rowNum) -> new Category(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category_type")
                )
        );
    }

    @GetMapping("/items")
    public List<PackingItem> items(
            @RequestParam(required = false) List<Long> categoryIds) {

        if (categoryIds == null) {
            categoryIds = List.of();
        }

        String sql = """
                SELECT DISTINCT i.id, i.name
                FROM items i
                JOIN item_categories ic ON ic.item_id = i.id
                JOIN categories c ON c.id = ic.category_id
                WHERE c.category_type = 'UNIVERSAL'
                """;

        if (!categoryIds.isEmpty()) {
            String placeholders = String.join(
                    ",",
                    Collections.nCopies(categoryIds.size(), "?")
            );

            sql += " OR c.id IN (" + placeholders + ")";
        }

        sql += " ORDER BY i.name";

        return jdbc.query(
                sql,
                (rs, rowNum) -> new PackingItem(
                        rs.getLong("id"),
                        rs.getString("name")
                ),
                categoryIds.toArray()
        );
    }
}
