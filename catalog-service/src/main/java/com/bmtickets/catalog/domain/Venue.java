package com.bmtickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "venue")
public class Venue {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String city;
    private String address;
    private int capacity;

    protected Venue() {
    }

    public Venue(String name, String city, String address, int capacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.city = city;
        this.address = address;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public int getCapacity() { return capacity; }
}
