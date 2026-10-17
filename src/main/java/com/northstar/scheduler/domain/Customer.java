package com.northstar.scheduler.domain;

public record Customer(long id, String name, String address, String postalCode,
                       String phone, String region, String country) {
    @Override public String toString() { return name + " (#" + id + ")"; }
}
