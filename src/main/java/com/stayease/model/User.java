package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing an application user (Customer or Admin).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @jakarta.persistence.ManyToMany(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinTable(
            name = "user_favorite_rooms",
            joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "room_id")
    )
    private java.util.Set<Room> favoriteRooms = new java.util.HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String fullName, String email, String passwordHash, String phone, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public java.util.Set<Room> getFavoriteRooms() {
        return favoriteRooms;
    }

    public void setFavoriteRooms(java.util.Set<Room> favoriteRooms) {
        this.favoriteRooms = favoriteRooms;
    }

    public boolean hasFavorite(Long roomId) {
        if (roomId == null || favoriteRooms == null) return false;
        return favoriteRooms.stream().anyMatch(r -> r.getId() != null && r.getId().equals(roomId));
    }

    public void addFavorite(Room room) {
        if (room != null && favoriteRooms != null) {
            favoriteRooms.add(room);
        }
    }

    public void removeFavorite(Room room) {
        if (room != null && favoriteRooms != null) {
            favoriteRooms.removeIf(r -> r.getId() != null && r.getId().equals(room.getId()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return id != null && Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
