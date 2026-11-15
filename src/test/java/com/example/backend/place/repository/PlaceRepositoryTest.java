package com.example.backend.place.repository;

import com.example.backend.place.domain.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class PlaceRepositoryTest {

    @Autowired
    private PlaceRepository placeRepository;

    private Place createPlace(String externalPlaceId, String name) {
        return Place.builder()
                .externalPlaceId(externalPlaceId)
                .name(name)
                .latitude(33.394)
                .longitude(126.2397)
                .build();
    }

    @Test
    @DisplayName("Place 저장 시 UUID와 생성 시각이 자동으로 채워진다")
    void savePlace() {
        Place saved = placeRepository.save(Place.builder()
                .externalPlaceId("ChIJ_XYZ")
                .name("협재해수욕장")
                .address("제주특별자치도 제주시 한림읍 협재리 2497-1")
                .latitude(33.394)
                .longitude(126.2397)
                .category("자연/해변")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("협재해수욕장");
        assertThat(saved.getExternalPlaceId()).isEqualTo("ChIJ_XYZ");
    }

    @Test
    @DisplayName("같은 externalPlaceId로 두 번 저장하면 예외가 발생한다")
    void duplicateExternalPlaceIdThrows() {
        placeRepository.save(createPlace("ChIJ_SAME", "장소 A"));

        assertThrows(DataIntegrityViolationException.class, () ->
                placeRepository.saveAndFlush(createPlace("ChIJ_SAME", "장소 B")));
    }

    @Test
    @DisplayName("externalPlaceId가 null인 장소는 여러 개 저장해도 예외가 없다")
    void nullExternalPlaceIdAllowedMultipleTimes() {
        placeRepository.saveAndFlush(createPlace(null, "장소 A"));
        placeRepository.saveAndFlush(createPlace(null, "장소 B"));

        assertThat(placeRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("externalPlaceId로 장소를 조회할 수 있다")
    void findByExternalPlaceId() {
        placeRepository.save(createPlace("ChIJ_FIND", "협재해수욕장"));

        Optional<Place> found = placeRepository.findByExternalPlaceId("ChIJ_FIND");
        Optional<Place> notFound = placeRepository.findByExternalPlaceId("ChIJ_NONE");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("협재해수욕장");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("이름의 일부 키워드로 장소를 검색할 수 있다")
    void searchByNameKeyword() {
        placeRepository.save(createPlace("id-1", "협재해수욕장"));
        placeRepository.save(createPlace("id-2", "협재 카페"));
        placeRepository.save(createPlace("id-3", "성산일출봉"));

        List<Place> result = placeRepository.findByNameContainingIgnoreCase("협재");

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(Place::getName)
                .containsExactlyInAnyOrder("협재해수욕장", "협재 카페");
    }

    @Test
    @DisplayName("이름 검색은 대소문자를 구분하지 않는다")
    void searchByNameIgnoreCase() {
        placeRepository.save(createPlace("id-4", "Starbucks Hyeopjae"));

        List<Place> result = placeRepository.findByNameContainingIgnoreCase("STARBUCKS");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Starbucks Hyeopjae");
    }
}
