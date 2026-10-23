package com.project.learning_platform.request;

import com.project.learning_platform.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9_-]{1,28}[A-Za-z0-9])$",
                message = "must be 3-30 characters: letters, digits, '-' or '_', starting and ending with a letter or digit")
        String username,

        @NotBlank
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @MaxUtf8Bytes(72)
        String password
) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", username=" + username + ", password=****]";
    }
}
