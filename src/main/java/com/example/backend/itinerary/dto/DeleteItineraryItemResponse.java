package com.example.backend.itinerary.dto;

/**
 * 일정 항목 삭제 응답. success는 ApiResponse가 이미 담고 있으므로 message만 내려준다.
 */
public record DeleteItineraryItemResponse(String message) {
    public static DeleteItineraryItemResponse deleted() {
        return new DeleteItineraryItemResponse("일정 항목이 삭제되었습니다.");
    }
}
