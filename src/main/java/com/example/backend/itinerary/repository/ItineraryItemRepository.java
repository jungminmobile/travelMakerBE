package com.example.backend.itinerary.repository;

import com.example.backend.itinerary.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {
    List<ItineraryItem> findByItineraryDay_Itinerary_TripIdOrderByDisplayOrderAsc(UUID tripId);

    /**
     * 일정 상세 조회용. 항목과 일차, 장소를 한 번의 쿼리로 가져와 N+1 쿼리를 막는다.
     * 장소가 없는 항목도 포함되도록 place는 left join fetch 한다.
     */
    @Query("""
            select i from ItineraryItem i
            join fetch i.itineraryDay d
            left join fetch i.place
            where d.itinerary.id = :itineraryId
            order by d.dayNumber asc, i.displayOrder asc
            """)
    List<ItineraryItem> findAllByItineraryIdWithPlace(@Param("itineraryId") UUID itineraryId);
}
