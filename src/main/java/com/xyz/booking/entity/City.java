package com.xyz.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "cities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cityId;

    @Column(nullable = false, unique = true, length = 100)
    private String cityName;

    @Column(nullable = false, length = 100)
    private String stateName;

    @Column(nullable = false, length = 100)
    private String countryName;

    @Column(length = 50)
    private String timezone;

    @OneToMany(mappedBy = "city", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Theatre> theatres;
}
