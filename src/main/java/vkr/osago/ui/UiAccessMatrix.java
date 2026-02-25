package vkr.osago.ui;

import vkr.osago.user.UserStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UiAccessMatrix {
    private UiAccessMatrix() {
    }

    public static List<String> publicPages() {
        return List.of(
                "/",
                "/index.html",
                "/login/index.html",
                "/register/index.html",
                "/insurance/osago/index.html"
        );
    }

    public static List<String> clientPages() {
        return List.of(
                "/cabinet/client/index.html",
                "/cabinet/client/chat/index.html",
                "/cabinet/client/claims/index.html",
                "/cabinet/client/claims/detail.html",
                "/cabinet/client/policies/index.html",
                "/cabinet/client/policies/detail.html"
        );
    }

    public static List<String> agentPages() {
        return List.of(
                "/cabinet/agent/index.html",
                "/cabinet/agent/chats/index.html",
                "/cabinet/agent/claims/index.html",
                "/cabinet/agent/claims/detail.html",
                "/cabinet/agent/policies/index.html"
        );
    }

    public static boolean isPageAllowed(UserStatus role, String path) {
        if (path == null || path.isBlank()) return false;
        if (publicPages().contains(path)) return true;
        if (role == null) return false;
        return switch (role) {
            case CLIENT -> clientPages().contains(path);
            case AGENT -> agentPages().contains(path);
            case ADMIN -> true;
        };
    }

    public static List<String> validateInput(UiFormType type, Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        if (type == null) {
            errors.add("formType is required");
            return errors;
        }
        switch (type) {
            case LOGIN -> {
                String email = trim(data.get("email"));
                String password = trim(data.get("password"));
                if (email == null || !email.contains("@")) errors.add("email is invalid");
                if (password == null || password.length() < 6) errors.add("password is invalid");
            }
            case CLAIM -> {
                String description = trim(data.get("description"));
                String phone = trim(data.get("phone"));
                if (description == null || description.length() < 10) errors.add("description is too short");
                if (phone == null || phone.length() < 10) errors.add("phone is invalid");
            }
            case UI_MESSAGE -> {
                String message = trim(data.get("message"));
                if (message == null || message.length() < 2) errors.add("message is too short");
            }
        }
        return errors;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
