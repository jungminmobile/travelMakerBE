package com.travelmaker.backend.itinerary.dto;

import com.travelmaker.backend.itinerary.domain.ItineraryStatus;

/**
 * 일정 이름/상태 수정 요청. 보낸 값만 수정하고 null인 값은 그대로 둔다.
 */
public record UpdateItineraryRequest(
        String title,
        ItineraryStatus status
) {
}
