package com.example.listThings;

import com.example.listThings.service.ItemService;
import com.example.listThings.service.ItemService.Item;
import com.example.listThings.service.ServiceException;
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

import java.net.URI;
import java.util.List;

@Controller
public class AdminController {

    private final ItemService itemService;

    public AdminController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/admin")
    public String admin() {
        return "forward:/admin.html";
    }

    @GetMapping("/api/admin/items")
    @ResponseBody
    public List<Item> items() {
        return itemService.items();
    }

    @PostMapping("/api/admin/items")
    public ResponseEntity<Void> create(@RequestBody CreateItem request) {
        long itemId = itemService.create(request.name(), request.categoryIds());
        return ResponseEntity.created(URI.create("/api/admin/items/" + itemId)).build();
    }

    @PutMapping("/api/admin/items/{id}")
    public ResponseEntity<Void> update(@PathVariable long id, @RequestBody UpdateItem request) {
        itemService.update(id, request.name(), request.categoryIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/items/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        itemService.delete(id);
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

    public record CreateItem(String name, List<String> categoryIds) {
    }

    public record UpdateItem(String name, List<String> categoryIds) {
    }
}
