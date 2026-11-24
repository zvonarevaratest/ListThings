package com.example.listThings;

import com.example.listThings.service.CategoryService;
import com.example.listThings.service.CategoryService.Category;
import com.example.listThings.service.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Slf4j
@Controller
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping({"/admin/categories", "/admin/activities"})
    public String page() {
        return "forward:/admin-categories.html";
    }

    @GetMapping("/api/admin/categories")
    @ResponseBody
    public List<Category> categories() {
        return categoryService.categories();
    }

    @PostMapping("/api/admin/categories")
    public ResponseEntity<Void> create(@RequestBody CategoryRequest request) {
        categoryService.create(request.name(), request.type(), request.itemIds());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> update(@PathVariable long id, @RequestBody CategoryRequest request) {
        categoryService.update(id, request.name(), request.type(), request.itemIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ServiceException.class)
    public ProblemDetail handleRequestError(ServiceException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_NAME -> HttpStatus.CONFLICT;
        };
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
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

    public record CategoryRequest(String name, String type, List<String> itemIds) {
    }
}
