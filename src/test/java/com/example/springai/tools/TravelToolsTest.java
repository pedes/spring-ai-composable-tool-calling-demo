package com.example.springai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TravelToolsTest {

    private final TravelTools tools = new TravelTools();

    @Test
    void findsFlightsSortedByPrice() {
        var flights = tools.findFlights("Madrid", "Amsterdam", "2026-07-06");

        assertThat(flights)
                .extracting(TravelTools.FlightOption::flightNumber)
                .containsExactly("U27888", "IB3724", "KL1500");
    }

    @Test
    void filtersHotelsByBudget() {
        var hotels = tools.findHotels("Amsterdam", 2, new BigDecimal("190.00"));

        assertThat(hotels)
                .extracting(TravelTools.HotelOption::name)
                .containsExactly("Harbor Light Hotel", "Canal House Studio");
    }

    @Test
    void estimatesBudget() {
        var budget = tools.estimateBudget(
                new BigDecimal("119.50"),
                new BigDecimal("156.50"),
                2,
                new BigDecimal("75.00"),
                new BigDecimal("120.00"));

        assertThat(budget.lodging()).isEqualByComparingTo("313.00");
        assertThat(budget.total()).isEqualByComparingTo("627.50");
    }
}
