package com.example.listThings;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class AdminController {

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/admin")
    public String admin() {
        return "forward:/admin.html";
    }

    @GetMapping("/api/admin/items")
    @ResponseBody
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

    @PostMapping("/api/admin/items")
    @Transactional
    public ResponseEntity<Void> create(@RequestBody CreateItem request) {
        String name = validatedName(request.name());
        Set<Long> categoryIds = validatedCategoryIds(request.categoryIds());

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
        return ResponseEntity.created(URI.create("/api/admin/items/" + itemId)).build();
    }

    @PutMapping("/api/admin/items/{id}")
    @Transactional
    public ResponseEntity<Void> update(@PathVariable long id, @RequestBody UpdateItem request) {
        String name = validatedName(request.name());
        Set<Long> categoryIds = validatedCategoryIds(request.categoryIds());
        try {
            requireItem(jdbc.update("UPDATE items SET name = ? WHERE id = ?", name, id));
        } catch (DuplicateKeyException exception) {
            throw duplicateName(exception);
        }
        // Name-only requests retain existing links. An empty array clears them.
        if (request.categoryIds() != null) {
            jdbc.update("DELETE FROM item_categories WHERE item_id = ?", id);
            insertCategoryLinks(id, categoryIds);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/items/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        // The foreign key deletes item_categories rows in the same statement.
        requireItem(jdbc.update("DELETE FROM items WHERE id = ?", id));
        return ResponseEntity.noContent().build();
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
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указана недопустимая категория.");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Одна из выбранных категорий больше не существует.", exception);
        }
    }

    private static String validatedName(String value) {
        String name = value == null ? "" : value.replaceAll("(?U)^\\s+|\\s+$", "");
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите название вещи.");
        }
        if (name.codePointCount(0, name.length()) > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Название должно содержать не более 255 символов.");
        }
        return name;
    }

    private static void requireItem(int affectedRows) {
        if (affectedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Вещь уже удалена или не существует.");
        }
    }

    private static ResponseStatusException duplicateName(DuplicateKeyException exception) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Вещь с таким названием уже существует.", exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleRequestError(ResponseStatusException exception) {
        return exception.getBody();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidBody() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Не удалось прочитать данные формы.");
    }

    public record CreateItem(String name, List<String> categoryIds) {
    }

    public record UpdateItem(String name, List<String> categoryIds) {
    }

    public record Item(String id, String name, List<Category> categories) {
    }

    public record Category(String id, String name) {
    }
}
