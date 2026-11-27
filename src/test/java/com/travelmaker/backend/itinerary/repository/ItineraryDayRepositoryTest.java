package com.travelmaker.backend.itinerary.repository;

import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryDay;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ItineraryDayRepositoryTest {

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private ItineraryDayRepository itineraryDayRepository;

    private Itinerary saveItinerary() {
        return itineraryRepository.save(Itinerary.builder()
                .tripId(UUID.randomUUID())
                .version(1)
                .title("제주 3박 4일")
                .status(ItineraryStatus.DRAFT)
                .createdBy(UUID.randomUUID())
                .build());
    }

    private ItineraryDay createDay(Itinerary itinerary, int dayNumber) {
        return ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(dayNumber)
                .date(LocalDate.of(2026, 11, 19).plusDays(dayNumber - 1))
                .build();
    }

    @Test
    @DisplayName("일차 저장 시 UUID가 채워지고 소속 일정이 연결된다")
    void saveDay() {
        Itinerary itinerary = saveItinerary();

        ItineraryDay saved = itineraryDayRepository.save(createDay(itinerary, 1));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItinerary().getId()).isEqualTo(itinerary.getId());
        assertThat(saved.getDayNumber()).isEqualTo(1);
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    @DisplayName("title 없이도 저장할 수 있다")
    void saveDayWithoutTitle() {
        ItineraryDay saved = itineraryDayRepository.saveAndFlush(createDay(saveItinerary(), 1));

        assertThat(saved.getTitle()).isNull();
    }

    @Test
    @DisplayName("title을 넣으면 함께 저장된다")
    void saveDayWithTitle() {
        ItineraryDay saved = itineraryDayRepository.saveAndFlush(ItineraryDay.builder()
                .itinerary(saveItinerary())
                .dayNumber(1)
                .date(LocalDate.of(2026, 11, 19))
                .title("서쪽 해안 드라이브")
                .build());

        assertThat(saved.getTitle()).isEqualTo("서쪽 해안 드라이브");
    }

    @Test
    @DisplayName("일정별 일차 목록은 해당 일정만 일차 순서대로 조회된다")
    void findByItineraryIdOrderByDayNumberAsc() {
        Itinerary itinerary = saveItinerary();
        Itinerary other = saveItinerary();
        itineraryDayRepository.save(createDay(itinerary, 3));
        itineraryDayRepository.save(createDay(itinerary, 1));
        itineraryDayRepository.save(createDay(itinerary, 2));
        itineraryDayRepository.save(createDay(other, 1));

        List<ItineraryDay> result =
                itineraryDayRepository.findByItineraryIdOrderByDayNumberAsc(itinerary.getId());

        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(ItineraryDay::getDayNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("일차가 없는 일정은 빈 목록을 반환한다")
    void findByItineraryIdEmpty() {
        Itinerary itinerary = saveItinerary();

        List<ItineraryDay> result =
                itineraryDayRepository.findByItineraryIdOrderByDayNumberAsc(itinerary.getId());

        assertThat(result).isEmpty();
    }
}
