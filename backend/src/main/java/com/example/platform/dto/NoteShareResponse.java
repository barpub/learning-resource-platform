package com.example.platform.dto;

public class NoteShareResponse {
    private String token;

    public NoteShareResponse() {
    }

    public NoteShareResponse(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
