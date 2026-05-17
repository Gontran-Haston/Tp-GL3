package com.devoir.gl.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.devoir.gl.entities.Bank;
import java.util.Optional;
import java.util.List;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {
	
	Optional<Bank> findBySwiftCode(String swiftCode);
	
	Optional<Bank> findByName(String name);
	
	List<Bank> findByCountry(String country);
	
}
