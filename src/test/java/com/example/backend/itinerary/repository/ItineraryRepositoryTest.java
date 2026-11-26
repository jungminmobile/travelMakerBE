package com.example.backend.itinerary.repository;

import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class ItineraryRepositoryTest {

    @Autowired
    private ItineraryRepository itineraryRepository;

    private Itinerary createItinerary(UUID tripId, Integer version, ItineraryStatus status) {
        return Itinerary.builder()
                .tripId(tripId)
                .version(version)
                .title("제주 3박 4일")
                .status(status)
                .createdBy(UUID.randomUUID())
                .build();
    }

    @Test
    @DisplayName("일정 저장 시 UUID와 생성 시각이 자동으로 채워진다")
    void saveItinerary() {
        Itinerary saved = itineraryRepository.save(
                createItinerary(UUID.randomUUID(), 1, ItineraryStatus.DRAFT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(ItineraryStatus.DRAFT);
    }

    @Test
    @DisplayName("generationJobId 없이도 저장할 수 있다")
    void saveWithoutGenerationJobId() {
        Itinerary saved = itineraryRepository.saveAndFlush(
                createItinerary(UUID.randomUUID(), 1, ItineraryStatus.DRAFT));

        assertThat(saved.getGenerationJobId()).isNull();
    }

    @Test
    @DisplayName("같은 여행에 같은 version을 저장하면 예외가 발생한다")
    void duplicateTripIdAndVersionThrows() {
        UUID tripId = UUID.randomUUID();
        itineraryRepository.save(createItinerary(tripId, 1, ItineraryStatus.DRAFT));

        assertThrows(DataIntegrityViolationException.class, () ->
                itineraryRepository.saveAndFlush(createItinerary(tripId, 1, ItineraryStatus.DRAFT)));
    }

    @Test
    @DisplayName("같은 여행이라도 version이 다르면 저장된다")
    void sameTripDifferentVersionAllowed() {
        UUID tripId = UUID.randomUUID();

        itineraryRepository.saveAndFlush(createItinerary(tripId, 1, ItineraryStatus.DRAFT));
        itineraryRepository.saveAndFlush(createItinerary(tripId, 2, ItineraryStatus.DRAFT));

        assertThat(itineraryRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("version이 같아도 여행이 다르면 저장된다")
    void sameVersionDifferentTripAllowed() {
        itineraryRepository.saveAndFlush(createItinerary(UUID.randomUUID(), 1, ItineraryStatus.DRAFT));
        itineraryRepository.saveAndFlush(createItinerary(UUID.randomUUID(), 1, ItineraryStatus.DRAFT));

        assertThat(itineraryRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("여행별 일정 목록은 해당 여행만 최신 버전 순으로 조회된다")
    void findByTripIdOrderByVersionDesc() {
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();
        itineraryRepository.save(createItinerary(tripId, 1, ItineraryStatus.ARCHIVED));
        itineraryRepository.save(createItinerary(tripId, 3, ItineraryStatus.ACTIVE));
        itineraryRepository.save(createItinerary(tripId, 2, ItineraryStatus.DRAFT));
        itineraryRepository.save(createItinerary(otherTripId, 1, ItineraryStatus.DRAFT));

        List<Itinerary> result = itineraryRepository.findByTripIdOrderByVersionDesc(tripId);

        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(Itinerary::getVersion)
                .containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("여행의 ACTIVE 일정을 조회할 수 있다")
    void findActiveItinerary() {
        UUID tripId = UUID.randomUUID();
        itineraryRepository.save(createItinerary(tripId, 1, ItineraryStatus.ARCHIVED));
        itineraryRepository.save(createItinerary(tripId, 2, ItineraryStatus.ACTIVE));
        itineraryRepository.save(createItinerary(tripId, 3, ItineraryStatus.DRAFT));

        Optional<Itinerary> active =
                itineraryRepository.findByTripIdAndStatus(tripId, ItineraryStatus.ACTIVE);

        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("ACTIVE 일정이 없으면 빈 결과를 반환한다")
    void findActiveItineraryEmpty() {
        UUID tripId = UUID.randomUUID();
        itineraryRepository.save(createItinerary(tripId, 1, ItineraryStatus.DRAFT));

        Optional<Itinerary> active =
                itineraryRepository.findByTripIdAndStatus(tripId, ItineraryStatus.ACTIVE);

        assertThat(active).isEmpty();
    }

    @Test
    @DisplayName("여행별 최대 버전을 조회하며 일정이 없으면 0을 반환한다")
    void findMaxVersionByTripId() {
        UUID tripId = UUID.randomUUID();
        assertThat(itineraryRepository.findMaxVersionByTripId(tripId)).isZero();

        itineraryRepository.save(createItinerary(tripId, 1, ItineraryStatus.ARCHIVED));
        itineraryRepository.save(createItinerary(tripId, 4, ItineraryStatus.DRAFT));
        itineraryRepository.save(createItinerary(UUID.randomUUID(), 10, ItineraryStatus.DRAFT));

        assertThat(itineraryRepository.findMaxVersionByTripId(tripId)).isEqualTo(4);
    }

    @Test
    @DisplayName("generationJobId 단건 및 일괄 조회가 동작한다")
    void findByGenerationJobId() {
        UUID tripId = UUID.randomUUID();
        UUID job1 = UUID.randomUUID();
        UUID job2 = UUID.randomUUID();

        Itinerary first = itineraryRepository.saveAndFlush(Itinerary.builder()
                .tripId(tripId)
                .version(1)
                .title("AI 일정 v1")
                .status(ItineraryStatus.DRAFT)
                .generationJobId(job1)
                .createdBy(UUID.randomUUID())
                .build());
        Itinerary second = itineraryRepository.saveAndFlush(Itinerary.builder()
                .tripId(tripId)
                .version(2)
                .title("AI 일정 v2")
                .status(ItineraryStatus.DRAFT)
                .generationJobId(job2)
                .createdBy(UUID.randomUUID())
                .build());

        assertThat(itineraryRepository.findByGenerationJobId(job1))
                .isPresent()
                .get()
                .extracting(Itinerary::getId)
                .isEqualTo(first.getId());

        assertThat(itineraryRepository.findAllByGenerationJobIdIn(List.of(job1, job2)))
                .extracting(Itinerary::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }
}
