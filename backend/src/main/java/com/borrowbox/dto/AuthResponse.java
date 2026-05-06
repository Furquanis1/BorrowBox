package com.borrowbox.dto;

import com.borrowbox.entity.User;

public record AuthResponse(String token, User user) {
}
