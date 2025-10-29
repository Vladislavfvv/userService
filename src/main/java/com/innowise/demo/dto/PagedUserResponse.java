package com.innowise.demo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagedUserResponse {
    private List<UserDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
