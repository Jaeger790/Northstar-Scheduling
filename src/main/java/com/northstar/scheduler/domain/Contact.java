package com.northstar.scheduler.domain;

public record Contact(long id, String name, String email) {
    @Override public String toString() { return name; }
}
