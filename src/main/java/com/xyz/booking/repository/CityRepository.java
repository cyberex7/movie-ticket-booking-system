package com.xyz.booking.repository;

import com.xyz.booking.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByCityNameIgnoreCase(String cityName);

    boolean existsByCityNameIgnoreCase(String cityName);
}
