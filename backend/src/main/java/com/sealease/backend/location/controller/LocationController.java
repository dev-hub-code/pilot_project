package com.sealease.backend.location.controller;

import com.sealease.backend.location.dto.LocationResponses.City;
import com.sealease.backend.location.dto.LocationResponses.Country;
import com.sealease.backend.location.dto.LocationResponses.State;
import com.sealease.backend.location.service.LocationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Address dropdowns, step by step: the countries; once one is chosen, its states; once a state is chosen, its
 * cities. Public and cacheable: the data changes only with a migration.
 */
@RestController
@RequestMapping("/api/v1/public/locations")
public class LocationController {

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

	private final LocationService locations;

	public LocationController(LocationService locations) {
		this.locations = locations;
	}

	@GetMapping("/countries")
	public ResponseEntity<List<Country>> countries() {
		return ResponseEntity.ok().cacheControl(CACHE).body(locations.countries());
	}

	@GetMapping("/countries/{countryCode}/states")
	public ResponseEntity<List<State>> states(@PathVariable String countryCode) {
		return ResponseEntity.ok().cacheControl(CACHE).body(locations.states(countryCode));
	}

	@GetMapping("/states/{stateId}/cities")
	public ResponseEntity<List<City>> cities(@PathVariable int stateId) {
		return ResponseEntity.ok().cacheControl(CACHE).body(locations.cities(stateId));
	}

}
