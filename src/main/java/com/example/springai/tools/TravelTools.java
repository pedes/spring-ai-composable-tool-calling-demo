package com.example.springai.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TravelTools {

    private static final Map<String, ZoneId> ZONES = Map.of(
            "amsterdam", ZoneId.of("Europe/Amsterdam"),
            "madrid", ZoneId.of("Europe/Madrid"),
            "london", ZoneId.of("Europe/London"),
            "new york", ZoneId.of("America/New_York"));

    private static final List<FlightOption> FLIGHTS = List.of(
            new FlightOption("KL1500", "Madrid", "Amsterdam", "2026-07-06T07:40", "2026-07-06T10:10", new BigDecimal("168.40")),
            new FlightOption("IB3724", "Madrid", "Amsterdam", "2026-07-06T12:15", "2026-07-06T14:45", new BigDecimal("143.90")),
            new FlightOption("U27888", "Madrid", "Amsterdam", "2026-07-06T19:20", "2026-07-06T21:50", new BigDecimal("119.50")),
            new FlightOption("BA442", "London", "Amsterdam", "2026-07-06T09:25", "2026-07-06T11:40", new BigDecimal("94.20")),
            new FlightOption("DL46", "New York", "Amsterdam", "2026-07-06T18:55", "2026-07-07T08:35", new BigDecimal("712.00")));

    private static final List<HotelOption> HOTELS = List.of(
            new HotelOption("Canal House Studio", "Amsterdam", 2, new BigDecimal("188.00"), "Jordaan"),
            new HotelOption("Museum Quarter Rooms", "Amsterdam", 3, new BigDecimal("221.00"), "Oud-Zuid"),
            new HotelOption("Harbor Light Hotel", "Amsterdam", 2, new BigDecimal("156.50"), "Eastern Docklands"),
            new HotelOption("Retiro Garden Stay", "Madrid", 2, new BigDecimal("132.00"), "Retiro"));

    @Tool(description = "Get the current local date and time for a city")
    public String currentLocalTime(@ToolParam(description = "City name, such as Amsterdam or Madrid") String city) {
        ZoneId zoneId = ZONES.getOrDefault(normalize(city), ZoneId.of("UTC"));
        return ZonedDateTime.now(zoneId).toString();
    }

    @Tool(description = "Get a deterministic travel weather summary for a city and date")
    public WeatherReport weather(
            @ToolParam(description = "City name, such as Amsterdam or Madrid") String city,
            @ToolParam(description = "Travel date in YYYY-MM-DD format") String date) {

        LocalDate travelDate = LocalDate.parse(date);
        String normalizedCity = normalize(city);
        int offset = Math.floorMod(normalizedCity.hashCode() + travelDate.getDayOfYear(), 5);

        return switch (offset) {
            case 0 -> new WeatherReport(city, date, "sunny", 22, "Light jacket optional.");
            case 1 -> new WeatherReport(city, date, "cloudy", 18, "Comfortable walking weather.");
            case 2 -> new WeatherReport(city, date, "light rain", 16, "Pack a rain shell.");
            case 3 -> new WeatherReport(city, date, "windy", 14, "Plan indoor backups.");
            default -> new WeatherReport(city, date, "warm", 25, "Hydrate and book shaded activities.");
        };
    }

    @Tool(description = "Find available flights between two cities for a date")
    public List<FlightOption> findFlights(
            @ToolParam(description = "Origin city") String origin,
            @ToolParam(description = "Destination city") String destination,
            @ToolParam(description = "Departure date in YYYY-MM-DD format") String date) {

        return FLIGHTS.stream()
                .filter(flight -> flight.origin().equalsIgnoreCase(origin))
                .filter(flight -> flight.destination().equalsIgnoreCase(destination))
                .filter(flight -> flight.departure().startsWith(date))
                .sorted(Comparator.comparing(FlightOption::price))
                .toList();
    }

    @Tool(description = "Book a flight by flight number and passenger name")
    public BookingConfirmation bookFlight(
            @ToolParam(description = "Flight number from findFlights") String flightNumber,
            @ToolParam(description = "Passenger full name") String passengerName) {

        FlightOption flight = FLIGHTS.stream()
                .filter(option -> option.flightNumber().equalsIgnoreCase(flightNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown flight: " + flightNumber));

        return new BookingConfirmation("flight", "FL-" + shortId(), passengerName, flight.price(),
                "Booked " + flight.flightNumber() + " from " + flight.origin() + " to " + flight.destination());
    }

    @Tool(description = "Find hotels in a city for a number of nights and maximum nightly budget")
    public List<HotelOption> findHotels(
            @ToolParam(description = "City name") String city,
            @ToolParam(description = "Number of nights") int nights,
            @ToolParam(description = "Maximum nightly budget in EUR") BigDecimal maxNightlyBudget) {

        return HOTELS.stream()
                .filter(hotel -> hotel.city().equalsIgnoreCase(city))
                .filter(hotel -> hotel.nightlyRate().compareTo(maxNightlyBudget) <= 0)
                .sorted(Comparator.comparing(HotelOption::nightlyRate))
                .toList();
    }

    @Tool(description = "Reserve a hotel by hotel name")
    public BookingConfirmation reserveHotel(
            @ToolParam(description = "Hotel name from findHotels") String hotelName,
            @ToolParam(description = "Guest full name") String guestName,
            @ToolParam(description = "Number of nights") int nights) {

        HotelOption hotel = HOTELS.stream()
                .filter(option -> option.name().equalsIgnoreCase(hotelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown hotel: " + hotelName));

        BigDecimal total = hotel.nightlyRate().multiply(BigDecimal.valueOf(nights));
        return new BookingConfirmation("hotel", "HT-" + shortId(), guestName, total,
                "Reserved " + hotel.name() + " in " + hotel.neighborhood() + " for " + nights + " nights");
    }

    @Tool(description = "Find attractions for a city and weather condition")
    public List<Attraction> findAttractions(
            @ToolParam(description = "City name") String city,
            @ToolParam(description = "Weather condition, such as sunny, cloudy, or rain") String weatherCondition) {

        if (!city.equalsIgnoreCase("Amsterdam")) {
            return List.of(
                    new Attraction("Historic center walk", "outdoor", new BigDecimal("0.00")),
                    new Attraction("Local food hall", "indoor", new BigDecimal("18.00")));
        }

        boolean wet = weatherCondition.toLowerCase(Locale.ROOT).contains("rain");
        if (wet) {
            return List.of(
                    new Attraction("Rijksmuseum", "indoor", new BigDecimal("24.50")),
                    new Attraction("Foodhallen", "indoor", new BigDecimal("20.00")),
                    new Attraction("Canal cruise with covered boat", "covered", new BigDecimal("19.00")));
        }

        return List.of(
                new Attraction("Vondelpark cycling loop", "outdoor", new BigDecimal("12.00")),
                new Attraction("Jordaan walking route", "outdoor", new BigDecimal("0.00")),
                new Attraction("Canal cruise", "outdoor", new BigDecimal("18.00")));
    }

    @Tool(description = "Estimate total trip cost from flight, hotel, activity, and food costs")
    public TripBudget estimateBudget(
            @ToolParam(description = "Flight cost in EUR") BigDecimal flightCost,
            @ToolParam(description = "Hotel nightly rate in EUR") BigDecimal hotelNightlyRate,
            @ToolParam(description = "Number of hotel nights") int nights,
            @ToolParam(description = "Activity budget in EUR") BigDecimal activities,
            @ToolParam(description = "Food budget in EUR") BigDecimal food) {

        BigDecimal lodging = hotelNightlyRate.multiply(BigDecimal.valueOf(nights));
        BigDecimal total = flightCost.add(lodging).add(activities).add(food);
        return new TripBudget(flightCost, lodging, activities, food, total);
    }

    public List<String> toolNames() {
        return List.of(
                "currentLocalTime",
                "weather",
                "findFlights",
                "bookFlight",
                "findHotels",
                "reserveHotel",
                "findAttractions",
                "estimateBudget");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public record WeatherReport(String city, String date, String condition, int temperatureCelsius, String packingAdvice) {
    }

    public record FlightOption(String flightNumber, String origin, String destination, String departure, String arrival,
            BigDecimal price) {
    }

    public record HotelOption(String name, String city, int stars, BigDecimal nightlyRate, String neighborhood) {
    }

    public record Attraction(String name, String setting, BigDecimal estimatedCost) {
    }

    public record TripBudget(BigDecimal flightCost, BigDecimal lodging, BigDecimal activities, BigDecimal food,
            BigDecimal total) {
    }

    public record BookingConfirmation(String type, String confirmationCode, String traveler, BigDecimal total,
            String details) {
    }
}
