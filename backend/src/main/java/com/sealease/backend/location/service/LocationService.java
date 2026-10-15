package com.sealease.backend.location.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.location.dto.LocationResponses.City;
import com.sealease.backend.location.dto.LocationResponses.Country;
import com.sealease.backend.location.dto.LocationResponses.State;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Countries, states and cities (reference data loaded by migrations V51-V52), alphabetical at each level. */
@Service
public class LocationService {

	private final JdbcTemplate jdbc;

	public LocationService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<Country> countries() {
		return jdbc.query("SELECT code, name, phone_code FROM countries ORDER BY name",
				(rs, i) -> new Country(rs.getString("code"), rs.getString("name"), rs.getString("phone_code")));
	}

	/** The states of a country (by ISO alpha-2 code); empty for a country without states. */
	@Transactional(readOnly = true)
	public List<State> states(String countryCode) {
		String code = countryCode.strip().toUpperCase(Locale.ROOT);
		Integer known = jdbc.queryForObject("SELECT count(*) FROM countries WHERE code = ?", Integer.class, code);
		if (known == null || known == 0) {
			throw new ResourceNotFoundException("Country", code);
		}
		return jdbc.query("SELECT id, name, code FROM states WHERE country_code = ? ORDER BY name",
				(rs, i) -> new State(rs.getInt("id"), rs.getString("name"), rs.getString("code")), code);
	}

	/** The cities of a state; empty for a state without cities. */
	@Transactional(readOnly = true)
	public List<City> cities(int stateId) {
		Integer known = jdbc.queryForObject("SELECT count(*) FROM states WHERE id = ?", Integer.class, stateId);
		if (known == null || known == 0) {
			throw new ResourceNotFoundException("State", stateId);
		}
		return jdbc.query("SELECT id, name FROM cities WHERE state_id = ? ORDER BY name",
				(rs, i) -> new City(rs.getInt("id"), rs.getString("name")), stateId);
	}

}
