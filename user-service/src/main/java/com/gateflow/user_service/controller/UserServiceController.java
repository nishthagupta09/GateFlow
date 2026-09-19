package com.gateflow.user_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserServiceController {

//    @GetMapping("users/{id}")
//    public String getUser(@PathVariable Long id){
//        return
//                """
//                {
//                    "id": %d,
//                    "name": "Alice",
//                    "service": "user-service"
//                }
//                """.formatted(id);
//    }

    @GetMapping("/users/{id}")
    public ResponseEntity<String> getUser(@PathVariable Long id) {

        if (id == 404) {
            return ResponseEntity
                    .status(404)
                    .body("""
                        {
                            "error": "User not found"
                        }
                        """);
        }

        return ResponseEntity.ok("""
            {
                "id": %d,
                "name": "Alice",
                "service": "user-service"
            }
            """.formatted(id));
    }

    @GetMapping("/users/retry-test")
    public ResponseEntity<String> retryTest() {

        return ResponseEntity
                .status(500)
                .body("""
                    {
                        "error": "Temporary failure for retry testing"
                    }
                    """);
    }


}
