package com.example.backend.auth.dto;

public record FirebaseIdentity(String uid, String email, String name, String picture) {}