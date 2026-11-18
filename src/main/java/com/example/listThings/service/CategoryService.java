package com.example.listThings.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final JdbcTemplate jdbc;

    public CategoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Category> categories() {
        return jdbc.query("""
                SELECT c.id, c.name, c.category_type, COUNT(ic.item_id) AS item_count
                FROM categories c
                LEFT JOIN item_categories ic ON ic.category_id = c.id
                GROUP BY c.id, c.name, c.category_type
                ORDER BY c.name
                """, (rs, rowNum) -> new Category(rs.getString("id"), rs.getString("name"),
                rs.getString("category_type"), rs.getLong("item_count")));
    }

    public void create(String nameValue, String typeValue) {
        String name = validatedName(nameValue);
        CategoryType type = validatedType(typeValue);
        jdbc.update("INSERT INTO categories (name, category_type) VALUES (?, ?)", name, type.name());
    }

    public void update(long id, String nameValue, String typeValue) {
        String name = validatedName(nameValue);
        CategoryType type = validatedType(typeValue);
        requireCategory(jdbc.update("UPDATE categories SET name = ?, category_type = ? WHERE id = ?",
                name, type.name(), id));
    }

    @Transactional
    public void delete(long id) {
        // fk_ic_category cascades to item_categories within this transaction.
        requireCategory(jdbc.update("DELETE FROM categories WHERE id = ?", id));
    }

    public List<CatalogCategory> catalogCategories() {
        return jdbc.query("""
                SELECT id, name, category_type
                FROM categories
                ORDER BY name
                """,
                (rs, rowNum) -> new CatalogCategory(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category_type")
                )
        );
    }

    private static String validatedName(String value) {
        String name = value == null ? "" : value.replaceAll("(?U)^\\s+|\\s+$", "");
        if (name.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT, "Введите название категории.");
        }
        if (name.codePointCount(0, name.length()) > 100) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT,
                    "Название категории должно содержать не более 100 символов.");
        }
        return name;
    }

    private static CategoryType validatedType(String value) {
        try {
            return CategoryType.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT, "Выберите допустимый тип категории.");
        }
    }

    private static void requireCategory(int affectedRows) {
        if (affectedRows == 0) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Категория уже удалена или не существует.");
        }
    }

    private enum CategoryType {
        UNIVERSAL, TRANSPORT, ACCOMMODATION
    }

    public record Category(String id, String name, String type, long itemCount) {
    }

    public record CatalogCategory(long id, String name, String type) {
    }
}
