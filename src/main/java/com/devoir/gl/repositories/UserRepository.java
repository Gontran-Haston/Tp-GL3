package com.devoir.gl.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.devoir.gl.entities.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
