package com.example.listThings.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CategoryService {

    private static final String INSERT_CATEGORY = "INSERT INTO categories (name, category_type) VALUES (?, ?)";

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

    @Transactional
    public void create(String nameValue, String typeValue, List<String> itemIdValues) {
        String name = validatedName(nameValue);
        CategoryType type = validatedType(typeValue);
        Set<Long> itemIds = validatedItemIds(itemIdValues);
        if (itemIds.isEmpty()) {
            jdbc.update(INSERT_CATEGORY, name, type.name());
            return;
        }
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(INSERT_CATEGORY, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, type.name());
            return statement;
        }, key);
        insertItemLinks(key.getKey().longValue(), itemIds);
    }

    @Transactional
    public void update(long id, String nameValue, String typeValue, List<String> itemIdValues) {
        String name = validatedName(nameValue);
        CategoryType type = validatedType(typeValue);
        Set<Long> itemIds = validatedItemIds(itemIdValues);
        requireCategory(jdbc.update("UPDATE categories SET name = ?, category_type = ? WHERE id = ?",
                name, type.name(), id));
        // Missing itemIds retains the existing links. An explicit empty list clears them.
        if (itemIdValues != null) {
            jdbc.update("DELETE FROM item_categories WHERE category_id = ?", id);
            insertItemLinks(id, itemIds);
        }
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

    private static Set<Long> validatedItemIds(List<String> values) {
        Set<Long> ids = new LinkedHashSet<>();
        if (values == null) {
            return ids;
        }
        for (String value : values) {
            try {
                long id = Long.parseLong(value);
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                ids.add(id);
            } catch (NumberFormatException exception) {
                throw new ServiceException(ServiceException.Kind.INVALID_INPUT, "Указан недопустимый ID вещи.");
            }
        }
        return ids;
    }

    private void insertItemLinks(long categoryId, Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        List<Object[]> links = itemIds.stream()
                .map(itemId -> new Object[]{itemId, categoryId})
                .toList();
        try {
            // The foreign key validates each item when inserting its link.
            jdbc.batchUpdate("INSERT INTO item_categories (item_id, category_id) VALUES (?, ?)", links);
        } catch (DataIntegrityViolationException exception) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT,
                    "Одна из выбранных вещей больше не существует. Откройте форму заново.", exception);
        }
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
