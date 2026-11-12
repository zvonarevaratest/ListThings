package com.example.listThings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

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