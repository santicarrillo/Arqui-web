package com.ticketya.pagos.repository;

import com.ticketya.pagos.entity.Reintegro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReintegroRepository extends JpaRepository<Reintegro, Long> {
}