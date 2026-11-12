package com.example.listThings;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final JdbcTemplate jdbcTemplate;

    public HelloController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/random-name")
    public String randomName() {

        String sql = "SELECT name FROM names ORDER BY RAND() LIMIT 1";

        return jdbcTemplate.queryForObject(sql, String.class);
    }
    @GetMapping("/hello")
    public ResponseEntity<String> hello(
            @RequestParam(required = false) String name) {

        if (name == null ||
                !name.matches("[a-zA-Zа-яА-ЯёЁ0-9]+([ -][a-zA-Zа-яА-ЯёЁ0-9]+)*")) {
            return ResponseEntity
                    .badRequest()
                    .body("Некорректное имя. Повторите ввод.");
        }

        return ResponseEntity.ok("Hello " + name);
    }
}