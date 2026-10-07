package com.example.repository;
import com.example.entity.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarreraRepository extends JpaRepository<Carrera, Integer> {
    // ---- Acá van las consultas de los incisos 2.f y 2.h (reporte) ----
}
