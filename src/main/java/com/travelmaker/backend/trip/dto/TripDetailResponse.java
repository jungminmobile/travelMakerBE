package com.travelmaker.backend.trip.dto;

import com.travelmaker.backend.trip.domain.*;
import com.travelmaker.backend.user.domain.User;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripDetailResponse(
        @JsonProperty("trip_id") UUID tripId,
        String title, String destination,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        Long budget, String currency, TripStatus status,
        List<TripDetailMemberResponse> members
        ) {

    public static TripDetailResponse from(Trip trip, List<TripDetailMemberResponse> members){
        return new TripDetailResponse(trip.getId(), trip.getTitle(), trip.getDestination(), trip.getStartDate(),
                trip.getEndDate(), trip.getBudget(), trip.getCurrency(), trip.getStatus(), members);
    }

    public record TripDetailMemberResponse(
            @JsonProperty("user_id") UUID userId,
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            TripMemberRole role, TripMemberStatus status,
            @JsonProperty("has_preference") boolean hasPreference,
            @JsonProperty("joined_at") Instant joinedAt
            ){
        public static TripDetailMemberResponse from(TripMember member, User user, boolean hasPreference){
            return new TripDetailMemberResponse(user.getId(), user.getNickname(),user.getProfileImageUrl(),
                    member.getRole(), member.getStatus(), hasPreference, member.getJoinedAt());
        }
    }
}
