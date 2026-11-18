package com.example.listThings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.util.List;

@Controller
public class AdminCategoryController {

    private static final Logger log = LoggerFactory.getLogger(AdminCategoryController.class);

    private final JdbcTemplate jdbc;

    public AdminCategoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/categories")
    public String page() {
        return "forward:/admin-categories.html";
    }

    @GetMapping("/api/admin/categories")
    @ResponseBody
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

    @PostMapping("/api/admin/categories")
    public ResponseEntity<Void> create(@RequestBody CategoryRequest request) {
        String name = validatedName(request.name());
        CategoryType type = validatedType(request.type());
        jdbc.update("INSERT INTO categories (name, category_type) VALUES (?, ?)", name, type.name());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> update(@PathVariable long id, @RequestBody CategoryRequest request) {
        String name = validatedName(request.name());
        CategoryType type = validatedType(request.type());
        requireCategory(jdbc.update("UPDATE categories SET name = ?, category_type = ? WHERE id = ?",
                name, type.name(), id));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/categories/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable long id) {
        // fk_ic_category cascades to item_categories within this transaction.
        requireCategory(jdbc.update("DELETE FROM categories WHERE id = ?", id));
        return ResponseEntity.noContent().build();
    }

    private static String validatedName(String value) {
        String name = value == null ? "" : value.replaceAll("(?U)^\\s+|\\s+$", "");
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите название категории.");
        }
        if (name.codePointCount(0, name.length()) > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Название категории должно содержать не более 100 символов.");
        }
        return name;
    }

    private static CategoryType validatedType(String value) {
        try {
            return CategoryType.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите допустимый тип категории.");
        }
    }

    private static void requireCategory(int affectedRows) {
        if (affectedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Категория уже удалена или не существует.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleRequestError(ResponseStatusException exception) {
        return exception.getBody();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidBody() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Не удалось прочитать данные формы.");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateName() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Категория с таким названием уже существует.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDatabaseError(DataAccessException exception) {
        log.error("Admin category database operation failed", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Не удалось выполнить операцию с категориями. Попробуйте ещё раз.");
    }

    private enum CategoryType {
        UNIVERSAL, TRANSPORT, ACCOMMODATION
    }

    public record CategoryRequest(String name, String type) {
    }

    public record Category(String id, String name, String type, long itemCount) {
    }
}
