package com.example.listThings.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ItemService {

    private final JdbcTemplate jdbc;

    public ItemService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Item> items() {
        return jdbc.query("""
                SELECT i.id AS item_id, i.name AS item_name,
                       c.id AS category_id, c.name AS category_name
                FROM items i
                LEFT JOIN item_categories ic ON ic.item_id = i.id
                LEFT JOIN categories c ON c.id = ic.category_id
                ORDER BY i.name, i.id, c.name
                """, rs -> {
            Map<Long, Item> items = new LinkedHashMap<>();
            while (rs.next()) {
                long itemId = rs.getLong("item_id");
                String itemName = rs.getString("item_name");
                Item item = items.computeIfAbsent(itemId,
                        id -> new Item(Long.toString(id), itemName, new ArrayList<>()));

                String categoryId = rs.getString("category_id");
                if (categoryId != null) {
                    item.categories().add(new Category(categoryId, rs.getString("category_name")));
                }
            }
            return new ArrayList<>(items.values());
        });
    }

    @Transactional
    public long create(String nameValue, List<String> categoryIdValues) {
        String name = validatedName(nameValue);
        Set<Long> categoryIds = validatedCategoryIds(categoryIdValues);

        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO items (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, name);
                return statement;
            }, key);
        } catch (DuplicateKeyException exception) {
            throw duplicateName(exception);
        }

        long itemId = key.getKey().longValue();
        insertCategoryLinks(itemId, categoryIds);
        return itemId;
    }

    @Transactional
    public void update(long id, String nameValue, List<String> categoryIdValues) {
        String name = validatedName(nameValue);
        Set<Long> categoryIds = validatedCategoryIds(categoryIdValues);
        try {
            requireItem(jdbc.update("UPDATE items SET name = ? WHERE id = ?", name, id));
        } catch (DuplicateKeyException exception) {
            throw duplicateName(exception);
        }
        // Name-only requests retain existing links. An empty array clears them.
        if (categoryIdValues != null) {
            jdbc.update("DELETE FROM item_categories WHERE item_id = ?", id);
            insertCategoryLinks(id, categoryIds);
        }
    }

    public void delete(long id) {
        // The foreign key deletes item_categories rows in the same statement.
        requireItem(jdbc.update("DELETE FROM items WHERE id = ?", id));
    }

    public List<PackingItem> packingItems(List<Long> categoryIds) {
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

    private static Set<Long> validatedCategoryIds(List<String> values) {
        Set<Long> ids = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                try {
                    long id = Long.parseLong(value);
                    if (id <= 0) {
                        throw new NumberFormatException();
                    }
                    ids.add(id);
                } catch (NumberFormatException exception) {
                    throw new ServiceException(ServiceException.Kind.INVALID_INPUT, "Указана недопустимая категория.");
                }
            }
        }
        return ids;
    }

    private void insertCategoryLinks(long itemId, Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return;
        }
        List<Object[]> links = categoryIds.stream()
                .map(categoryId -> new Object[]{itemId, categoryId})
                .toList();
        try {
            // The foreign key validates each ID at insertion, including concurrent category deletions.
            jdbc.batchUpdate("INSERT INTO item_categories (item_id, category_id) VALUES (?, ?)", links);
        } catch (DataIntegrityViolationException exception) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT,
                    "Одна из выбранных категорий больше не существует.", exception);
        }
    }

    private static String validatedName(String value) {
        String name = value == null ? "" : value.replaceAll("(?U)^\\s+|\\s+$", "");
        if (name.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT, "Введите название вещи.");
        }
        if (name.codePointCount(0, name.length()) > 255) {
            throw new ServiceException(ServiceException.Kind.INVALID_INPUT,
                    "Название должно содержать не более 255 символов.");
        }
        return name;
    }

    private static void requireItem(int affectedRows) {
        if (affectedRows == 0) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Вещь уже удалена или не существует.");
        }
    }

    private static ServiceException duplicateName(DuplicateKeyException exception) {
        return new ServiceException(ServiceException.Kind.DUPLICATE_NAME, "Вещь с таким названием уже существует.", exception);
    }

    public record PackingItem(long id, String name) {
    }

    public record Item(String id, String name, List<Category> categories) {
    }

    public record Category(String id, String name) {
    }
}
