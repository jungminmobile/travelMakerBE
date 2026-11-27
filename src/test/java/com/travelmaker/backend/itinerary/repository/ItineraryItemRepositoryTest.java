package com.travelmaker.backend.itinerary.repository;

import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryDay;
import com.travelmaker.backend.itinerary.domain.ItineraryItem;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import com.travelmaker.backend.place.domain.Place;
import com.travelmaker.backend.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ItineraryItemRepositoryTest {

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private ItineraryDayRepository itineraryDayRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ItineraryItemRepository itineraryItemRepository;

    @Autowired
    private EntityManager entityManager;

    private ItineraryDay saveDay() {
        Itinerary itinerary = itineraryRepository.save(Itinerary.builder()
                .tripId(UUID.randomUUID())
                .version(1)
                .title("제주 3박 4일")
                .status(ItineraryStatus.DRAFT)
                .createdBy(UUID.randomUUID())
                .build());

        return itineraryDayRepository.save(ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(1)
                .date(LocalDate.of(2026, 11, 19))
                .build());
    }

    private Place savePlace() {
        return placeRepository.save(Place.builder()
                .name("성산일출봉")
                .latitude(33.4580)
                .longitude(126.9425)
                .build());
    }

    @Test
    @DisplayName("항목 저장 시 UUID가 채워지고 일차·장소가 연결된다")
    void saveItem() {
        ItineraryDay day = saveDay();
        Place place = savePlace();

        ItineraryItem saved = itineraryItemRepository.saveAndFlush(ItineraryItem.builder()
                .itineraryDay(day)
                .place(place)
                .displayOrder(1)
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(8, 0))
                .title("일출 보기")
                .description("해 뜨기 30분 전 도착")
                .estimatedCost(5000L)
                .recommendationReason("일출 명소")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItineraryDay().getId()).isEqualTo(day.getId());
        assertThat(saved.getPlace().getId()).isEqualTo(place.getId());
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(saved.getEstimatedCost()).isEqualTo(5000L);
        assertThat(saved.getRecommendationReason()).isEqualTo("일출 명소");
    }

    @Test
    @DisplayName("장소 없이 수동 항목을 저장할 수 있고 비용은 0으로 설정된다")
    void saveItemWithoutPlace() {
        ItineraryItem saved = itineraryItemRepository.saveAndFlush(ItineraryItem.builder()
                .itineraryDay(saveDay())
                .displayOrder(1)
                .title("숙소 휴식")
                .build());

        assertThat(saved.getPlace()).isNull();
        assertThat(saved.getStartTime()).isNull();
        assertThat(saved.getEstimatedCost()).isEqualTo(0L);
    }

    @Test
    @DisplayName("필수값(일차, 순서, 제목)이 없으면 생성 시점에 예외가 난다")
    void requiredFieldsMustNotBeNull() {
        ItineraryDay day = saveDay();

        assertThatThrownBy(() -> ItineraryItem.builder()
                .displayOrder(1).title("일출 보기").build())
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> ItineraryItem.builder()
                .itineraryDay(day).title("일출 보기").build())
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> ItineraryItem.builder()
                .itineraryDay(day).displayOrder(1).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update로 시간·순서·비용을 바꾸면 DB에 반영된다")
    void updateItem() {
        ItineraryItem saved = itineraryItemRepository.saveAndFlush(ItineraryItem.builder()
                .itineraryDay(saveDay())
                .displayOrder(1)
                .title("점심")
                .estimatedCost(10000L)
                .build());

        saved.update(LocalTime.of(12, 0), LocalTime.of(13, 0), 2, 15000L);
        itineraryItemRepository.flush();
        entityManager.clear();

        ItineraryItem found = itineraryItemRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(found.getEndTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(found.getDisplayOrder()).isEqualTo(2);
        assertThat(found.getEstimatedCost()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("update에서 순서를 null로 바꿀 수 없다")
    void updateWithNullDisplayOrder() {
        ItineraryItem saved = itineraryItemRepository.saveAndFlush(ItineraryItem.builder()
                .itineraryDay(saveDay())
                .displayOrder(1)
                .title("점심")
                .build());

        assertThatThrownBy(() -> saved.update(null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
