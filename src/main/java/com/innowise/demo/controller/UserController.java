package com.innowise.demo.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.createUser(dto));
    }

    @GetMapping("/id")
    public ResponseEntity<UserDto> getUserById(@RequestParam Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping
    public ResponseEntity<PagedUserResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(userService.findAllUsers(page, size));
    }


    //find user by email named
    @GetMapping("/email/named/{email}")
    public  ResponseEntity<UserDto> getUserByEmailNamed(@PathVariable String email) {
         return ResponseEntity.ok(userService.getUserByEmailNamed(email));
    }

    //find user by email named
    @GetMapping("/email/jpql/{email}")
    public ResponseEntity<UserDto> getUserByEmailJSQL(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmailJPQl(email));
    }

    //find user by email named
    @GetMapping("/email/native/{email}")
    public ResponseEntity<UserDto> getUserByEmailNative(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmailNative(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
