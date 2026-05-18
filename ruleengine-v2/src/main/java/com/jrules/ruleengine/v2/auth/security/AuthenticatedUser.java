package com.jrules.ruleengine.v2.auth.security;

public record AuthenticatedUser(String userId, String email, String name) {}
